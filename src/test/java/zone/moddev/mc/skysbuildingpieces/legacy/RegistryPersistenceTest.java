package zone.moddev.mc.skysbuildingpieces.legacy;

import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegistryPersistenceTest {
    @TempDir
    Path temporary;

    @AfterEach
    void clearWorld() {
        LegacyBridge.stop();
    }

    @Test
    void completeSnapshotSurvivesIndependentWriteOrderAndInputMutation() throws Exception {
        // Native startup checks cover event registration; these tests isolate the saved-data callbacks.
        Path directory = java.nio.file.Files.createDirectories(temporary.resolve("world"));
        CompoundTag expected = snapshot();
        for (boolean coreFirst : new boolean[] {true, false}) {
            CompoundTag input = new CompoundTag();
            input.put("FML", expected.copy());
            CompoundTag independentRead = input.getCompoundOrEmpty("FML").copy();
            LegacyBridge.captureLegacyLevelData(input, directory);
            input.getCompoundOrEmpty("FML").putString("changed_after_read", "Must not leak into the snapshot");
            CompoundTag output = new CompoundTag();
            if (coreFirst) LegacyBridge.writeLegacyLevelData(output);
            output.put("FML", independentRead.copy());
            if (!coreFirst) LegacyBridge.writeLegacyLevelData(output);
            assertEquals(expected, output.getCompoundOrEmpty("FML"));
            assertEquals(expected, independentRead);
        }
    }

    @Test
    void aNewWorldAndAStoppedSessionCannotInheritAnotherWorldsRegistry() throws Exception {
        Path first = java.nio.file.Files.createDirectories(temporary.resolve("first"));
        Path second = java.nio.file.Files.createDirectories(temporary.resolve("second"));
        CompoundTag input = new CompoundTag();
        input.put("FML", snapshot());
        LegacyBridge.captureLegacyLevelData(input, first);
        LegacyBridge.stop();
        CompoundTag stopped = new CompoundTag();
        LegacyBridge.writeLegacyLevelData(stopped);
        assertTrue(stopped.getCompoundOrEmpty("FML").isEmpty());
        LegacyBridge.captureLegacyLevelData(new CompoundTag(), second);
        CompoundTag fresh = new CompoundTag();
        LegacyBridge.writeLegacyLevelData(fresh);
        assertTrue(fresh.getCompoundOrEmpty("FML").isEmpty());
    }

    private CompoundTag snapshot() {
        CompoundTag data = new CompoundTag();
        CompoundTag registries = new CompoundTag();
        for (String registry : new String[] {"minecraft:blocks", "minecraft:items", "minecraft:biomes"}) {
            CompoundTag table = new CompoundTag();
            ListTag entries = new ListTag();
            CompoundTag entry = new CompoundTag();
            entry.putString("K", "fixture:retained_" + registry.substring(registry.indexOf(':') + 1));
            entry.putInt("V", 207);
            entries.add(entry);
            table.put("ids", entries);
            table.putString("uninterpreted_table_field", "Keep it");
            registries.put(registry, table);
        }
        data.put("Registries", registries);
        data.putString("uninterpreted_root_field", "Keep this too");
        return data;
    }
}
