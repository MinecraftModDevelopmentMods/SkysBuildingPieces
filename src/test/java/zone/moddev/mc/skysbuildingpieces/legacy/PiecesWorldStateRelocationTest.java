package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiecesWorldStateRelocationTest {
    @TempDir
    Path world;

    @Test
    void copiesOldStateOnceAndKeepsTheMoreRecentCounters() throws Exception {
        String filename = PiecesWorldState.NAME + ".dat";
        Path original = Files.createDirectories(world.resolve("data")).resolve(filename);
        Path destination = world.resolve("dimensions/minecraft/overworld/data/skysbuildingpieces")
                .resolve(filename);
        CompoundTag saved = new CompoundTag();
        PiecesWorldState state = new PiecesWorldState();
        state.addChunk("vanilla", 12);
        state.addItems("biomesoplenty", 7);
        saved.put("data", state.saveTag(new CompoundTag()));
        NbtIo.writeCompressed(saved, original);
        byte[] source = Files.readAllBytes(original);

        PiecesWorldState.relocateLegacyState(world);
        assertArrayEquals(source, Files.readAllBytes(destination));
        assertArrayEquals(source, Files.readAllBytes(original));

        state.addChunk("vanilla", 3);
        saved.put("data", state.saveTag(new CompoundTag()));
        NbtIo.writeCompressed(saved, destination);
        byte[] newer = Files.readAllBytes(destination);
        PiecesWorldState.relocateLegacyState(world);
        assertArrayEquals(newer, Files.readAllBytes(destination));
        assertArrayEquals(source, Files.readAllBytes(original));
        assertEquals(15, state.blocks);
    }
}
