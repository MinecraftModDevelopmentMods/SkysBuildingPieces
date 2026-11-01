package zone.moddev.mc.skysbuildingpieces.legacy;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.SaveFormat;
import net.minecraftforge.fml.WorldPersistenceHooks;
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
    void completeSnapshotSurvivesEitherNativeHookOrder() throws Exception {
        LegacyBridge.registerPersistence();
        Field field = WorldPersistenceHooks.class.getDeclaredField("worldPersistenceHooks");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<WorldPersistenceHooks.WorldPersistenceHook> original =
                new ArrayList<>((List<WorldPersistenceHooks.WorldPersistenceHook>) field.get(null));
        WorldPersistenceHooks.WorldPersistenceHook sky = original.get(original.size() - 1);
        CompoundNBT expected = snapshot();
        CompoundNBT otherRead = new CompoundNBT();
        WorldPersistenceHooks.WorldPersistenceHook other = new WorldPersistenceHooks.WorldPersistenceHook() {
            public String getModId() { return "FML"; }
            public CompoundNBT getDataForWriting(SaveFormat.LevelSave save, IServerConfiguration information) {
                return otherRead.copy();
            }
            public void readData(SaveFormat.LevelSave save, IServerConfiguration information, CompoundNBT data) {
                otherRead.merge(data.copy());
            }
        };
        SaveFormat format = new SaveFormat(temporary.resolve("saves"), temporary.resolve("backups"), null);
        try (SaveFormat.LevelSave save = format.createAccess("world")) {
            for (List<WorldPersistenceHooks.WorldPersistenceHook> order : Arrays.asList(
                    Arrays.asList(sky, other), Arrays.asList(other, sky))) {
                field.set(null, new ArrayList<>(order));
                CompoundNBT input = new CompoundNBT();
                input.put("FML", expected.copy());
                WorldPersistenceHooks.handleWorldDataLoad(save, null, input);
                input.getCompound("FML").putString("changed_after_read", "Must not leak into the snapshot");
                CompoundNBT output = new CompoundNBT();
                WorldPersistenceHooks.handleWorldDataSave(save, null, output);
                assertEquals(expected, output.getCompound("FML"));
                assertEquals(expected, otherRead);
            }
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void aNewWorldAndAStoppedSessionCannotInheritAnotherWorldsRegistry() throws Exception {
        LegacyBridge.registerPersistence();
        SaveFormat format = new SaveFormat(temporary.resolve("saves"), temporary.resolve("backups"), null);
        try (SaveFormat.LevelSave first = format.createAccess("first");
                SaveFormat.LevelSave second = format.createAccess("second")) {
            CompoundNBT input = new CompoundNBT();
            input.put("FML", snapshot());
            WorldPersistenceHooks.handleWorldDataLoad(first, null, input);
            LegacyBridge.stop();
            CompoundNBT stopped = new CompoundNBT();
            WorldPersistenceHooks.handleWorldDataSave(first, null, stopped);
            assertTrue(stopped.getCompound("FML").isEmpty());
            WorldPersistenceHooks.handleWorldDataLoad(second, null, new CompoundNBT());
            CompoundNBT fresh = new CompoundNBT();
            WorldPersistenceHooks.handleWorldDataSave(second, null, fresh);
            assertTrue(fresh.getCompound("FML").isEmpty());
        }
    }

    private CompoundNBT snapshot() {
        CompoundNBT data = new CompoundNBT();
        CompoundNBT registries = new CompoundNBT();
        for (String registry : new String[] {"minecraft:blocks", "minecraft:items", "minecraft:biomes"}) {
            CompoundNBT table = new CompoundNBT();
            ListNBT entries = new ListNBT();
            CompoundNBT entry = new CompoundNBT();
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
