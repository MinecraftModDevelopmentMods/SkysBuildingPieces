package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.ListNBT;
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

        CompoundNBT expected = SourceLevelData.forWorld(directory);
        writeLevel(directory, 2586, 400, "biomesoplenty:tropic_beach");
        SourceLevelData.capture(directory);
        assertEquals(1976, expected.getInt("DataVersion"));
        assertEquals(600, expected.getCompound("Registry").getList("ids", 10).getCompound(0).getInt("V"));
        assertEquals(expected, SourceLevelData.forWorld(directory));

        expected.getCompound("Registry").remove("ids");
        assertTrue(SourceLevelData.forWorld(directory).getCompound("Registry").contains("ids", 9));
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
        CompoundNBT root = new CompoundNBT();
        CompoundNBT data = new CompoundNBT();
        data.putInt("DataVersion", version);
        root.put("Data", data);

        CompoundNBT entry = new CompoundNBT();
        entry.putString("K", name);
        entry.putInt("V", number);
        ListNBT entries = new ListNBT();
        entries.add(entry);
        CompoundNBT registry = new CompoundNBT();
        registry.put("ids", entries);
        CompoundNBT registries = new CompoundNBT();
        registries.put("minecraft:biome", registry);
        CompoundNBT forge = new CompoundNBT();
        forge.put("Registries", registries);
        root.put("fml", forge);

        try (OutputStream output = Files.newOutputStream(world.resolve("level.dat"))) {
            CompressedStreamTools.writeCompressed(root, output);
        }
    }
}
