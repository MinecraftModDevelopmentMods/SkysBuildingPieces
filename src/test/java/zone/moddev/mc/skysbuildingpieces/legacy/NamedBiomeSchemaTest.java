package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap;

/** Uses a real BOP save, with build-only aliases so the core has no BOP dependency. */
class NamedBiomeSchemaTest {
    @TempDir
    Path temporary;

    @Test
    void namedBiomeVolumeSurvivesNativeFixingIncludingTheNewHeightPlanes() throws Exception {
        check(false);
    }

    @Test
    void mixedVolumesRetainNativeRenamesAndTheirVerticalBoundaries() throws Exception {
        check(true);
    }

    @Test
    void conflictingOrIncompletePendingRecoveryStopsWithoutChangingTheTag() {
        for (String key : new String[] {"skysbuildingpieces_biomes", "skysbuildingpieces_biome_names"}) {
            CompoundTag root = new CompoundTag();
            CompoundTag level = new CompoundTag();
            level.putIntArray("Biomes", new int[1024]);
            level.putString(key, "invalid");
            root.put("Level", level);
            CompoundTag before = root.copy();
            assertThrows(LegacyBridge.RecoveryAbort.class,
                    () -> HistoricalBiomes.preserveNamed(root, new int[1024], Map.of()));
            assertEquals(before, root);
        }
    }

    private void check(boolean mixed) throws Exception {
        MinecraftTestBootstrap.registerVanilla();
        Path fixture = Path.of("src/testFixtures/biomes/saved-bop-1.15.2.nbt");
        byte[] bytes = Files.readAllBytes(fixture);
        assertEquals("FBDF087B300AE3F9DAA6D55D46049E49051EBD931C52FCDF3734BEA234FDB4F5",
                java.util.HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        CompoundTag source = NbtIo.readCompressed(fixture.toFile());
        int[] cells = source.getCompound("Level").getIntArray("Biomes");
        assertEquals(1024, cells.length);
        for (int cell = 0; cell < cells.length; cell++) {
            assertEquals(52, cells[cell], "The original gravel-beach fixture changed");
            if (mixed && (cell >> 4) % 3 != 0) {
                cells[cell] = (cell & 1) == 0 ? 17 : 35;
            }
        }
        source.getCompound("Level").putIntArray("Biomes", cells);
        CompoundTag original = source.copy();
        try (ChunkStorage storage = new ChunkStorage(temporary, DataFixers.getDataFixer(), false)) {
            CompoundTag control = storage.upgradeChunkTag(Level.OVERWORLD, () -> null,
                    source.copy(), Optional.empty());
            CompoundTag prepared = source.copy();
            HistoricalBiomes.preserveNamed(prepared, cells, Map.of(52, "minecraft:stony_shore"));
            CompoundTag once = prepared.copy();
            HistoricalBiomes.preserveNamed(prepared, cells, Map.of(52, "minecraft:stony_shore"));
            assertEquals(once, prepared, "Two early wrappers must stage the same recovery safely");
            CompoundTag fixed = storage.upgradeChunkTag(Level.OVERWORLD, () -> null,
                    prepared, Optional.empty());
            HistoricalBiomes.finish(fixed);
            assertFalse(fixed.contains("skysbuildingpieces_biomes"));
            assertFalse(fixed.contains("skysbuildingpieces_biome_names"));
            ListTag sections = fixed.getList("sections", 10);
            ListTag nativeSections = control.getList("sections", 10);
            assertTrue(sections.size() >= 24, "The native Overworld height expansion must run");
            int checked = 0;
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                CompoundTag section = sections.getCompound(sectionIndex);
                for (int cell = 0; cell < 64; cell++) {
                    int height = Math.max(0, Math.min(63, section.getByte("Y") * 4 + (cell >> 4)));
                    int sourceIndex = height * 16 + (cell & 15);
                    String expected = cells[sourceIndex] == 52 ? "minecraft:stony_shore"
                            : biome(nativeSections.getCompound(sectionIndex).getCompound("biomes"), cell);
                    assertEquals(expected, biome(section.getCompound("biomes"), cell),
                            "section=" + section.getByte("Y") + ", cell=" + cell);
                    checked++;
                }
            }
            assertTrue(checked >= 1536);
            once = fixed.copy();
            HistoricalBiomes.finish(fixed);
            assertEquals(once, fixed, "Two return wrappers must finish recovery only once");
        }
        assertEquals(original, source);
        assertArrayEquals(bytes, Files.readAllBytes(fixture));
    }

    private static String biome(CompoundTag container, int cell) {
        ListTag palette = container.getList("palette", 8);
        assertFalse(palette.isEmpty());
        if (palette.size() == 1) {
            return palette.getString(0);
        }
        int bits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
        int perLong = 64 / bits;
        long[] data = container.getLongArray("data");
        return palette.getString((int) (data[cell / perLong] >>> (cell % perLong * bits) & ((1L << bits) - 1)));
    }
}
