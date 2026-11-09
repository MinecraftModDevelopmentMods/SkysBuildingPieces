package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAccountingTest {
    @Test
    void flattenedChunkKeepsItsPendingAccountingAndFinishesOnlySupportedRecovery() {
        CompoundTag chunk = new CompoundTag();
        chunk.putString("Status", "minecraft:heightmaps");
        chunk.putBoolean("SkysBuildingPiecesPreserveChunk", true);
        chunk.putLong("skysbuildingpieces_delta", 26);
        chunk.putLong("skysbuildingpieces_additional_vanilla_delta", 4);
        CompoundTag modules = new CompoundTag();
        modules.putLong("biomesoplenty", 601);
        chunk.put("skysbuildingpieces_module_delta", modules);
        CompoundTag coverage = new CompoundTag();
        coverage.putInt("vanilla", 1);
        chunk.put("skysbuildingpieces_coverage", coverage);
        chunk.putString("future_addon_note", "Keep unknown data");

        LegacyBridge.finalizeLegacyChunk(chunk);

        assertEquals("full", chunk.getStringOr("Status", ""));
        assertFalse(chunk.contains("SkysBuildingPiecesPreserveChunk"));
        assertEquals(26, chunk.getLongOr("skysbuildingpieces_delta", 0L));
        assertEquals(4, chunk.getLongOr("skysbuildingpieces_additional_vanilla_delta", 0L));
        assertEquals(modules, chunk.getCompoundOrEmpty("skysbuildingpieces_module_delta"));
        assertEquals(coverage, chunk.getCompoundOrEmpty("skysbuildingpieces_coverage"));
        assertEquals("Keep unknown data", chunk.getStringOr("future_addon_note", ""));
        CompoundTag once = chunk.copy();
        LegacyBridge.finalizeLegacyChunk(chunk);
        assertEquals(once, chunk, "Returning through a second wrapper must be harmless");

        CompoundTag unrelated = new CompoundTag();
        unrelated.putString("Status", "minecraft:heightmaps");
        LegacyBridge.finalizeLegacyChunk(unrelated);
        assertEquals("minecraft:heightmaps", unrelated.getStringOr("Status", ""));
    }

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
        assertEquals(26, MigrationEvents.chunkLevel(root).getLongOr("skysbuildingpieces_delta", 0L));
        assertEquals(markers, MigrationEvents.chunkLevel(root).getCompoundOrEmpty("skysbuildingpieces_coverage"));
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
        assertEquals(26, level.getLongOr("skysbuildingpieces_delta", 0L));
        assertEquals(4, level.getLongOr("skysbuildingpieces_additional_vanilla_delta", 0L));
        assertEquals(7, level.getLongOr("skysbuildingpieces_retired_delta", 0L));
        assertEquals(modules, level.getCompoundOrEmpty("skysbuildingpieces_module_delta"));
        assertEquals(modules, level.getCompoundOrEmpty("skysbuildingpieces_additional_module_delta"));
        assertEquals(modules, level.getCompoundOrEmpty("skysbuildingpieces_historical_delta"));
        assertFalse(root.contains("skysbuildingpieces_delta"));
        assertEquals("Keep unknown data", level.getStringOr("future_addon_note", ""));

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
