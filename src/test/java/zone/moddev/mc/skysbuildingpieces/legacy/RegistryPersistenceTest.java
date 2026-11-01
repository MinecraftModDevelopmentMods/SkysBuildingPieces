package zone.moddev.mc.skysbuildingpieces.legacy;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.fmllegacy.WorldPersistenceHooks;
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
        CompoundTag expected = snapshot();
        CompoundTag otherRead = new CompoundTag();
        WorldPersistenceHooks.WorldPersistenceHook other = new WorldPersistenceHooks.WorldPersistenceHook() {
            public String getModId() { return "FML"; }
            public CompoundTag getDataForWriting(LevelStorageSource.LevelStorageAccess save, WorldData information) {
                return otherRead.copy();
            }
            public void readData(LevelStorageSource.LevelStorageAccess save, WorldData information, CompoundTag data) {
                otherRead.merge(data.copy());
            }
        };
        LevelStorageSource format = new LevelStorageSource(temporary.resolve("saves"), temporary.resolve("backups"), null);
        try (LevelStorageSource.LevelStorageAccess save = format.createAccess("world")) {
            for (List<WorldPersistenceHooks.WorldPersistenceHook> order : Arrays.asList(
                    Arrays.asList(sky, other), Arrays.asList(other, sky))) {
                field.set(null, new ArrayList<>(order));
                CompoundTag input = new CompoundTag();
                input.put("FML", expected.copy());
                WorldPersistenceHooks.handleWorldDataLoad(save, null, input);
                input.getCompound("FML").putString("changed_after_read", "Must not leak into the snapshot");
                CompoundTag output = new CompoundTag();
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
        LevelStorageSource format = new LevelStorageSource(temporary.resolve("saves"), temporary.resolve("backups"), null);
        try (LevelStorageSource.LevelStorageAccess first = format.createAccess("first");
                LevelStorageSource.LevelStorageAccess second = format.createAccess("second")) {
            CompoundTag input = new CompoundTag();
            input.put("FML", snapshot());
            WorldPersistenceHooks.handleWorldDataLoad(first, null, input);
            LegacyBridge.stop();
            CompoundTag stopped = new CompoundTag();
            WorldPersistenceHooks.handleWorldDataSave(first, null, stopped);
            assertTrue(stopped.getCompound("FML").isEmpty());
            WorldPersistenceHooks.handleWorldDataLoad(second, null, new CompoundTag());
            CompoundTag fresh = new CompoundTag();
            WorldPersistenceHooks.handleWorldDataSave(second, null, fresh);
            assertTrue(fresh.getCompound("FML").isEmpty());
        }
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
