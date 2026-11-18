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
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
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
        CompoundTag source = NbtIo.readCompressed(fixture, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        int[] cells = source.getCompoundOrEmpty("Level").getIntArray("Biomes").orElseGet(() -> new int[0]);
        assertEquals(1024, cells.length);
        for (int cell = 0; cell < cells.length; cell++) {
            assertEquals(52, cells[cell], "The original gravel-beach fixture changed");
            if (mixed && (cell >> 4) % 3 != 0) {
                cells[cell] = (cell & 1) == 0 ? 17 : 35;
            }
        }
        source.getCompoundOrEmpty("Level").putIntArray("Biomes", cells);
        CompoundTag original = source.copy();
        try (SimpleRegionStorage storage = new SimpleRegionStorage(new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", Level.OVERWORLD, "chunk"),
                temporary, DataFixers.getDataFixer(), false, net.minecraft.util.datafix.DataFixTypes.CHUNK)) {
            CompoundTag control = storage.upgradeChunkTag(source.copy(), 0, MinecraftTestBootstrap.overworldUpgradeContext(), net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
            CompoundTag prepared = source.copy();
            HistoricalBiomes.preserveNamed(prepared, cells, Map.of(52, "minecraft:stony_shore"));
            CompoundTag once = prepared.copy();
            HistoricalBiomes.preserveNamed(prepared, cells, Map.of(52, "minecraft:stony_shore"));
            assertEquals(once, prepared, "Two early wrappers must stage the same recovery safely");
            CompoundTag fixed = storage.upgradeChunkTag(prepared, 0, MinecraftTestBootstrap.overworldUpgradeContext(), net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
            HistoricalBiomes.finish(fixed);
            assertFalse(fixed.contains("skysbuildingpieces_biomes"));
            assertFalse(fixed.contains("skysbuildingpieces_biome_names"));
            ListTag sections = fixed.getListOrEmpty("sections");
            ListTag nativeSections = control.getListOrEmpty("sections");
            assertTrue(sections.size() >= 24, "The native Overworld height expansion must run");
            int checked = 0;
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                CompoundTag section = sections.getCompoundOrEmpty(sectionIndex);
                for (int cell = 0; cell < 64; cell++) {
                    int height = Math.max(0, Math.min(63, section.getByteOr("Y", (byte) 0) * 4 + (cell >> 4)));
                    int sourceIndex = height * 16 + (cell & 15);
                    String expected = cells[sourceIndex] == 52 ? "minecraft:stony_shore"
                            : biome(nativeSections.getCompoundOrEmpty(sectionIndex).getCompoundOrEmpty("biomes"), cell);
                    assertEquals(expected, biome(section.getCompoundOrEmpty("biomes"), cell),
                            "section=" + section.getByteOr("Y", (byte) 0) + ", cell=" + cell);
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
        ListTag palette = container.getListOrEmpty("palette");
        assertFalse(palette.isEmpty());
        if (palette.size() == 1) {
            return palette.getStringOr(0, "");
        }
        int bits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
        int perLong = 64 / bits;
        long[] data = container.getLongArray("data").orElseGet(() -> new long[0]);
        return palette.getString((int) (data[cell / perLong] >>> (cell % perLong * bits) & ((1L << bits) - 1))).orElseThrow();
    }
}
