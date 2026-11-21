package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
        NumericStateBridge.begin(snapshot, new ListTag());

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

        ListTag stored = NumericStateBridge.saveStaging();

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

        NumericStateBridge.begin(snapshot, new ListTag());

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

        NumericStateBridge.begin(snapshot, new ListTag());

        LegacyBridge.RecoveryAbort failure = assertThrows(LegacyBridge.RecoveryAbort.class,
                () -> NumericStateBridge.encode(Blocks.STONE.defaultBlockState()));

        assertTrue(failure.getMessage().contains("1.12.2"));
        assertEquals(0, NumericStateBridge.saveStaging().size());
    }

    @Test
    void restoredStagingTargetsUseRegisteredRecoveryBeforeBlockDeserialization() {
        HistoricalItems.register("staging_target_test",
                root -> root.getCompoundOrEmpty("BlockState").getStringOr("Name", "").equals("missing:retired_native_log"),
                root -> root.getCompoundOrEmpty("BlockState").putString("Name", "minecraft:birch_log"));

        CompoundTag state = new CompoundTag();
        CompoundTag properties = new CompoundTag();
        CompoundTag entry = new CompoundTag();
        ListTag saved = new ListTag();

        properties.putString("axis", "z");
        state.putString("Name", "missing:retired_native_log");
        state.put("Properties", properties);
        entry.putInt("encoded", 2000 << 4 | 7);
        entry.put("state", state);
        saved.add(entry);

        ListTag before = saved.copy();

        NumericStateBridge.begin(new LinkedHashMap<>(), saved);

        CompoundTag restored = NumericStateBridge.saveStaging().getCompoundOrEmpty(0);
        CompoundTag expected = state.copy();

        expected.putString("Name", "minecraft:birch_log");
        assertEquals(2000 << 4 | 7, restored.getIntOr("encoded", 0));
        assertEquals(expected, restored.getCompoundOrEmpty("state"));
        assertEquals(before, saved);
    }

    @Test
    void savedGrassPathTargetUsesDirtPathWithoutChangingItsEncodingOrSourceTag() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:grass_path");
        CompoundTag entry = new CompoundTag();
        entry.putInt("encoded", 11198);
        entry.put("state", state);
        ListTag saved = new ListTag();
        saved.add(entry);
        ListTag original = saved.copy();

        NumericStateBridge.begin(new LinkedHashMap<>(), saved);

        assertEquals(original, saved, "Reading a sidecar must not mutate the supplied NBT");
        assertEquals(Blocks.DIRT_PATH.defaultBlockState(), NumericStateBridge.stagedState(11198));
        ListTag current = NumericStateBridge.saveStaging();
        assertEquals(11198, current.getCompoundOrEmpty(0).getIntOr("encoded", 0));
        assertEquals("minecraft:dirt_path", current.getCompoundOrEmpty(0).getCompoundOrEmpty("state").getStringOr("Name", ""));
        NumericStateBridge.begin(new LinkedHashMap<>(), current);
        assertEquals(current, NumericStateBridge.saveStaging(), "A reload must not repeat recovery");
        assertEquals(11198, NumericStateBridge.encode(Blocks.DIRT_PATH.defaultBlockState()));
    }

    @Test
    void currentStoneSlabTargetKeepsItsMeaningWhenRestored() {
        CompoundTag entry = new CompoundTag();
        entry.putInt("encoded", 2000 << 4);
        entry.put("state", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.STONE_SLAB.defaultBlockState()));
        ListTag saved = new ListTag();
        saved.add(entry);

        NumericStateBridge.begin(new LinkedHashMap<>(), saved);

        assertEquals(Blocks.STONE_SLAB.defaultBlockState(), NumericStateBridge.stagedState(2000 << 4));
        assertNotEquals(Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), NumericStateBridge.stagedState(2000 << 4));
        assertEquals(saved, NumericStateBridge.saveStaging());
    }

    @Test
    void corruptSidecarsAndOutOfRangeSourceNumbersFailClearly() {
        Map<Integer, String> snapshot = new LinkedHashMap<>();

        snapshot.put(256, "othermod:saved_block");

        CompoundTag entry = new CompoundTag();

        entry.putInt("encoded", 256 << 4);
        entry.put("state", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.GOLD_BLOCK.defaultBlockState()));

        ListTag conflict = new ListTag();

        conflict.add(entry);
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> NumericStateBridge.begin(snapshot, conflict));
        snapshot.put(4096, "othermod:invalid");
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> NumericStateBridge.begin(snapshot, new ListTag()));
    }
}
