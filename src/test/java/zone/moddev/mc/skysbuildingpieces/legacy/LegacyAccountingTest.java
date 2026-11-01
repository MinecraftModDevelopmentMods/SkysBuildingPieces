package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAccountingTest {
    @Test
    void nativeRootAndDirectLevelExposeTheSameAccountingAndCoverageData() {
        CompoundTag root = new CompoundTag();
        CompoundTag level = new CompoundTag();
        CompoundTag markers = new CompoundTag();
        markers.putInt("vanilla", 1);
        level.put("skysbuildingpieces_coverage", markers);
        root.put("Level", level);
        root.putLong("skysbuildingpieces_delta", 26);
        LegacyBridge.finalizeLegacyChunk(root);

        assertSame(level, MigrationEvents.chunkLevel(root));
        assertSame(level, MigrationEvents.chunkLevel(level));
        assertEquals(26, MigrationEvents.chunkLevel(root).getLong("skysbuildingpieces_delta"));
        assertEquals(markers, MigrationEvents.chunkLevel(root).getCompound("skysbuildingpieces_coverage"));
        MigrationEvents.chunkLevel(root).remove("skysbuildingpieces_delta");
        assertFalse(level.contains("skysbuildingpieces_delta"));
    }

    @Test
    void pendingAccountingTravelsWithTheLevelDataPassedToForge36LoadEvents() {
        CompoundTag root = new CompoundTag();
        CompoundTag level = new CompoundTag();

        level.putString("future_addon_note", "Keep unknown data");
        root.put("Level", level);
        root.putLong("skysbuildingpieces_delta", 26);
        root.putLong("skysbuildingpieces_additional_vanilla_delta", 4);
        root.putLong("skysbuildingpieces_retired_delta", 7);

        CompoundTag modules = new CompoundTag();

        modules.putLong("biomesoplenty", 601);
        root.put("skysbuildingpieces_module_delta", modules.copy());
        root.put("skysbuildingpieces_additional_module_delta", modules.copy());
        root.put("skysbuildingpieces_historical_delta", modules.copy());
        LegacyBridge.finalizeLegacyChunk(root);
        assertEquals(26, level.getLong("skysbuildingpieces_delta"));
        assertEquals(4, level.getLong("skysbuildingpieces_additional_vanilla_delta"));
        assertEquals(7, level.getLong("skysbuildingpieces_retired_delta"));
        assertEquals(modules, level.getCompound("skysbuildingpieces_module_delta"));
        assertEquals(modules, level.getCompound("skysbuildingpieces_additional_module_delta"));
        assertEquals(modules, level.getCompound("skysbuildingpieces_historical_delta"));
        assertFalse(root.contains("skysbuildingpieces_delta"));
        assertEquals("Keep unknown data", level.getString("future_addon_note"));

        CompoundTag once = root.copy();

        LegacyBridge.finalizeLegacyChunk(root);
        assertEquals(once, root, "The native before-return hook must be idempotent");
    }

    @Test
    void conflictingAccountingStopsBeforeMovingAnyFields() {
        CompoundTag root = new CompoundTag();
        CompoundTag level = new CompoundTag();

        root.put("Level", level);
        root.putLong("skysbuildingpieces_delta", 26);
        root.putLong("skysbuildingpieces_retired_delta", 7);
        level.putLong("skysbuildingpieces_retired_delta", 8);

        CompoundTag before = root.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyBridge.finalizeLegacyChunk(root));
        assertEquals(before, root);
    }
}
