package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap;
import static org.junit.jupiter.api.Assertions.*;

class NumericStateBridgeTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.registerVanilla();
    }

    @AfterEach
    void restoreTable() {
        NumericStateBridge.reset();
    }

    @Test
    void stagingUsesFreeSourceNumbersAndPacksSixteenStatesPerNumber() {
        Map<Integer, String> snapshot = new LinkedHashMap<>();

        snapshot.put(256, "othermod:saved_block");
        snapshot.put(258, "othermod:another_saved_block");
        NumericStateBridge.begin(snapshot, new ListNBT());

        int states = 0;
        Map<BlockState, Integer> encoded = new LinkedHashMap<>();

        for (BlockState state : Blocks.OAK_STAIRS.getStateDefinition().getPossibleStates()) {
            int number = NumericStateBridge.encode(state);

            assertNotEquals(256, number >>> 4);
            assertNotEquals(258, number >>> 4);
            assertTrue(number >>> 4 >= 256);
            encoded.put(state, number);
            states++;
        }

        ListNBT stored = NumericStateBridge.saveStaging();

        assertEquals(states, stored.size());

        long stagingNumbers = encoded.values().stream().map(number -> number >>> 4).distinct().count();

        assertEquals((states + 15) / 16, stagingNumbers);

        for (Map.Entry<BlockState, Integer> entry : encoded.entrySet()) {
            assertEquals(entry.getValue().intValue(), NumericStateBridge.encode(entry.getKey()));
        }

        NumericStateBridge.begin(snapshot, stored);
        assertEquals(stored, NumericStateBridge.saveStaging());

        for (Map.Entry<BlockState, Integer> entry : encoded.entrySet()) {
            assertEquals(entry.getValue().intValue(), NumericStateBridge.encode(entry.getKey()));
        }
    }

    @Test
    void partialStagingNumberIsReusedAfterReload() {
        Map<Integer, String> snapshot = new LinkedHashMap<>();

        NumericStateBridge.begin(snapshot, new ListNBT());

        int first = NumericStateBridge.encode(Blocks.GOLD_BLOCK.defaultBlockState());

        NumericStateBridge.begin(snapshot, NumericStateBridge.saveStaging());

        int second = NumericStateBridge.encode(Blocks.IRON_BLOCK.defaultBlockState());

        assertEquals(first + 1, second);
        assertEquals(first >>> 4, second >>> 4);
    }

    @Test
    void aFullSourceRegistryStopsWithoutOverwritingAnotherMod() {
        Map<Integer, String> snapshot = new LinkedHashMap<>();

        for (int number = 0; number < 4096; number++) {
            snapshot.put(number, "othermod:block_" + number);
        }

        NumericStateBridge.begin(snapshot, new ListNBT());

        LegacyBridge.RecoveryAbort failure = assertThrows(LegacyBridge.RecoveryAbort.class,
                () -> NumericStateBridge.encode(Blocks.STONE.defaultBlockState()));

        assertTrue(failure.getMessage().contains("1.12.2"));
        assertEquals(0, NumericStateBridge.saveStaging().size());
    }

    @Test
    void restoredStagingTargetsUseRegisteredRecoveryBeforeBlockDeserialization() {
        HistoricalItems.register("staging_target_test",
                root -> root.getCompound("BlockState").getString("Name").equals("missing:retired_native_log"),
                root -> root.getCompound("BlockState").putString("Name", "minecraft:birch_log"));

        CompoundNBT state = new CompoundNBT();
        CompoundNBT properties = new CompoundNBT();
        CompoundNBT entry = new CompoundNBT();
        ListNBT saved = new ListNBT();

        properties.putString("axis", "z");
        state.putString("Name", "missing:retired_native_log");
        state.put("Properties", properties);
        entry.putInt("encoded", 2000 << 4 | 7);
        entry.put("state", state);
        saved.add(entry);

        ListNBT before = saved.copy();

        NumericStateBridge.begin(new LinkedHashMap<>(), saved);

        CompoundNBT restored = NumericStateBridge.saveStaging().getCompound(0);
        CompoundNBT expected = state.copy();

        expected.putString("Name", "minecraft:birch_log");
        assertEquals(2000 << 4 | 7, restored.getInt("encoded"));
        assertEquals(expected, restored.getCompound("state"));
        assertEquals(before, saved);
    }

    @Test
    void corruptSidecarsAndOutOfRangeSourceNumbersFailClearly() {
        Map<Integer, String> snapshot = new LinkedHashMap<>();

        snapshot.put(256, "othermod:saved_block");

        CompoundNBT entry = new CompoundNBT();

        entry.putInt("encoded", 256 << 4);
        entry.put("state", net.minecraft.nbt.NBTUtil.writeBlockState(Blocks.GOLD_BLOCK.defaultBlockState()));

        ListNBT conflict = new ListNBT();

        conflict.add(entry);
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> NumericStateBridge.begin(snapshot, conflict));
        snapshot.put(4096, "othermod:invalid");
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> NumericStateBridge.begin(snapshot, new ListNBT()));
    }
}
