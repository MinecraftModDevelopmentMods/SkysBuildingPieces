package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundNBT;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAccountingTest {
    @Test
    void pendingAccountingTravelsWithTheLevelDataPassedToForge31LoadEvents() {
        CompoundNBT root = new CompoundNBT();
        CompoundNBT level = new CompoundNBT();

        level.putString("future_addon_note", "Keep unknown data");
        root.put("Level", level);
        root.putLong("skysbuildingpieces_delta", 26);
        root.putLong("skysbuildingpieces_additional_vanilla_delta", 4);
        root.putLong("skysbuildingpieces_retired_delta", 7);

        CompoundNBT modules = new CompoundNBT();

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

        CompoundNBT once = root.copy();

        LegacyBridge.finalizeLegacyChunk(root);
        assertEquals(once, root, "The native before-return hook must be idempotent");
    }

    @Test
    void conflictingAccountingStopsBeforeMovingAnyFields() {
        CompoundNBT root = new CompoundNBT();
        CompoundNBT level = new CompoundNBT();

        root.put("Level", level);
        root.putLong("skysbuildingpieces_delta", 26);
        root.putLong("skysbuildingpieces_retired_delta", 7);
        level.putLong("skysbuildingpieces_retired_delta", 8);

        CompoundNBT before = root.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyBridge.finalizeLegacyChunk(root));
        assertEquals(before, root);
    }
}
