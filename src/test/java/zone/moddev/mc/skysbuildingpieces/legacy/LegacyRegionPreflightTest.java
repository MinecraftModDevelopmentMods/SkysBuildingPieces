package zone.moddev.mc.skysbuildingpieces.legacy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRegionPreflightTest {
    @TempDir
    Path world;

    @Test
    void anUntouchedNativeRegionContainsNoChunksAndNeedsNoHeader() throws Exception {
        MinecraftTestBootstrap.registerVanilla();
        Path directory = Files.createDirectories(world.resolve("region"));
        Path file = directory.resolve("r.0.0.mca");

        try (RegionFile nativeRegion = new RegionFile(new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", net.minecraft.world.level.Level.OVERWORLD, "chunk"),
                file, directory, false)) {
            assertEquals(0, Files.size(file));
            assertNull(nativeRegion.getChunkDataInputStream(new ChunkPos(0, 0)));
            assertDoesNotThrow(() -> LegacyPreflight.validate(world.toFile(), Collections.emptyMap()));
            assertEquals(0, Files.size(file), "Preflight must not change a native empty region");
        }
    }

    @Test
    void aNonemptyTruncatedHeaderStillStopsBeforeAnyWorldChange() throws Exception {
        Path directory = Files.createDirectories(world.resolve("region"));
        Path file = directory.resolve("r.0.0.mca");

        for (int length : new int[] { 1, 4096, 8191 }) {
            byte[] original = new byte[length];
            original[length - 1] = 1;
            Files.write(file, original);
            LegacyBridge.RecoveryAbort failure = assertThrows(LegacyBridge.RecoveryAbort.class,
                    () -> LegacyPreflight.validate(world.toFile(), Collections.emptyMap()));
            assertTrue(failure.getMessage().contains("malformed region header"));
            assertArrayEquals(original, Files.readAllBytes(file));
        }
    }
}
