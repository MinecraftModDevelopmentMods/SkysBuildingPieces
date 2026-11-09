package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SourceLevelDataTest {
    @TempDir
    Path directory;

    @AfterEach
    void clearSnapshot() {
        SourceLevelData.clear();
    }

    @Test
    void anEarlySaveCannotReplaceTheSourceRegistry() throws Exception {
        writeLevel(directory, 1976, 600, "biomesoplenty:gravel_beach");
        byte[] source = Files.readAllBytes(directory.resolve("level.dat"));
        SourceLevelData.capture(directory);
        assertArrayEquals(source, Files.readAllBytes(directory.resolve("level.dat")));

        CompoundTag expected = SourceLevelData.forWorld(directory);
        writeLevel(directory, 2586, 400, "biomesoplenty:tropic_beach");
        SourceLevelData.capture(directory);
        assertEquals(1976, expected.getIntOr("DataVersion", 0));
        assertEquals(600, expected.getCompoundOrEmpty("Registry").getListOrEmpty("ids").getCompoundOrEmpty(0).getIntOr("V", 0));
        assertEquals(expected, SourceLevelData.forWorld(directory));

        expected.getCompoundOrEmpty("Registry").remove("ids");
        assertTrue(SourceLevelData.forWorld(directory).getCompoundOrEmpty("Registry").getList("ids").isPresent());
    }

    @Test
    void anotherWorldOrANewWorldCannotReuseThePreviousSnapshot() throws Exception {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        writeLevel(first, 1976, 600, "biomesoplenty:gravel_beach");
        SourceLevelData.capture(first);
        assertTrue(SourceLevelData.forWorld(second).isEmpty());

        SourceLevelData.capture(second);
        assertTrue(SourceLevelData.forWorld(first).isEmpty());
        assertTrue(SourceLevelData.forWorld(second).isEmpty());
    }

    @Test
    void unreadableSourceDataStopsRecoveryWithoutChangingTheFile() throws Exception {
        byte[] broken = {1, 2, 3, 4};
        Path level = directory.resolve("level.dat");
        Files.write(level, broken);
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> SourceLevelData.capture(directory));
        assertArrayEquals(broken, Files.readAllBytes(level));
        assertTrue(SourceLevelData.forWorld(directory).isEmpty());
    }

    private void writeLevel(Path world, int version, int number, String name) throws Exception {
        CompoundTag root = new CompoundTag();
        CompoundTag data = new CompoundTag();
        data.putInt("DataVersion", version);
        root.put("Data", data);

        CompoundTag entry = new CompoundTag();
        entry.putString("K", name);
        entry.putInt("V", number);
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag registry = new CompoundTag();
        registry.put("ids", entries);
        CompoundTag registries = new CompoundTag();
        registries.put("minecraft:biome", registry);
        CompoundTag forge = new CompoundTag();
        forge.put("Registries", registries);
        root.put("fml", forge);

        try (OutputStream output = Files.newOutputStream(world.resolve("level.dat"))) {
            NbtIo.writeCompressed(root, output);
        }
    }
}
