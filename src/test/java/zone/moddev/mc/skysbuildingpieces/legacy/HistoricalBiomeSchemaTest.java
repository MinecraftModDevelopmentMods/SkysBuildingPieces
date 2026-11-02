package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap;

class HistoricalBiomeSchemaTest {
    @TempDir
    Path temporary;

    @Test
    void savedModBiomeSurvivesTheActualNativeChunkUpgrader() throws Exception {
        checkUpgrade(false);
    }

    @Test
    void mixedBiomePalettesKeepVanillaFixesAndSampleTheOriginalQuartCentres() throws Exception {
        checkUpgrade(true);
    }

    @Test
    void malformedPendingDataStopsBeforeChangingAnySection() {
        for (boolean missingNames : new boolean[] {true, false}) {
            CompoundTag chunk = new CompoundTag();
            chunk.putIntArray("skysbuildingpieces_biomes", new int[256]);
            ListTag names = new ListTag();
            for (int column = 0; column < 256; column++) {
                names.add(StringTag.valueOf("minecraft:stony_shore"));
            }
            if (!missingNames) {
                chunk.put("skysbuildingpieces_biome_names", names);
            }
            ListTag sections = new ListTag();
            for (int sectionIndex = 0; sectionIndex < 2; sectionIndex++) {
                CompoundTag section = new CompoundTag();
                CompoundTag biomes = new CompoundTag();
                ListTag palette = new ListTag();
                palette.add(StringTag.valueOf("minecraft:plains"));
                if (sectionIndex == 1) {
                    palette.add(StringTag.valueOf("minecraft:desert"));
                    // Two entries require packed cell data, deliberately absent here.
                }
                biomes.put("palette", palette);
                section.put("biomes", biomes);
                sections.add(section);
            }
            chunk.put("sections", sections);
            CompoundTag before = chunk.copy();
            assertThrows(LegacyBridge.RecoveryAbort.class, () -> HistoricalBiomes.finish(chunk));
            assertEquals(before, chunk, "Validation must finish before any section is changed");
        }
    }

    private void checkUpgrade(boolean mixed) throws Exception {
        MinecraftTestBootstrap.registerVanilla();
        Path fixture = Path.of("src/testFixtures/biomes/saved-bop-1.12.2.nbt");
        byte[] original = Files.readAllBytes(fixture);
        String hash = java.util.HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(original));
        assertEquals("1140725D866D437EDEF10E36C898EE8EAAC67D1F4A928F56EE0B0DA8802F1BC4", hash);
        CompoundTag source = NbtIo.readCompressed(fixture.toFile());
        byte[] columns = source.getCompound("Level").getByteArray("Biomes");
        if (mixed) {
            // Alternate real mod-biome columns with vanilla IDs whose names changed.
            for (int row = 0; row < 16; row++) {
                for (int column = 0; column < 16; column++) {
                    if ((column / 4) % 2 == 1) {
                        columns[row * 16 + column] = (byte) (row < 8 ? 17 : 35);
                    }
                }
            }
            source.getCompound("Level").putByteArray("Biomes", columns);
        }
        Field active = HistoricalBiomes.class.getDeclaredField("active");
        active.setAccessible(true);
        Object previous = active.get(null);
        try (ChunkStorage storage = new ChunkStorage(temporary, DataFixers.getDataFixer(), false)) {
            CompoundTag control = storage.upgradeChunkTag(Level.OVERWORLD, () -> null,
                    source.copy(), Optional.empty());
            // This build-only alias stands in for an add-on's registered replacement.
            // The fixture remains untouched and no production BOP mapping is added.
            active.set(null, new HistoricalBiomes.Mappings(
                    Collections.singletonMap(40, "biomesoplenty:gravel_beach"),
                    Collections.singletonMap("biomesoplenty:gravel_beach", "minecraft:stony_shore"),
                    name -> 901));
            CompoundTag prepared = source.copy();
            assertTrue(HistoricalBiomes.prepare(prepared));
            CompoundTag fixed = storage.upgradeChunkTag(Level.OVERWORLD, () -> null,
                    prepared, Optional.empty());
            assertFalse(fixed.contains("Level"), "The native upgrader must reach the 1.18 chunk format");
            HistoricalBiomes.finish(fixed);
            assertFalse(fixed.contains("skysbuildingpieces_biomes"), "Pending recovery must be completed");
            assertFalse(fixed.contains("skysbuildingpieces_biome_names"));
            ListTag sections = fixed.getList("sections", 10);
            ListTag nativeSections = control.getList("sections", 10);
            assertTrue(sections.size() >= 16);
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                CompoundTag actual = sections.getCompound(sectionIndex).getCompound("biomes");
                CompoundTag baseline = nativeSections.getCompound(sectionIndex).getCompound("biomes");
                for (int cell = 0; cell < 64; cell++) {
                    int column = (cell & 3) * 4 + 2;
                    int row = (cell >> 2 & 3) * 4 + 2;
                    String expected = (columns[row * 16 + column] & 255) == 40
                            ? "minecraft:stony_shore" : biome(baseline, cell);
                    assertEquals(expected, biome(actual, cell), "section=" + sectionIndex + ", cell=" + cell);
                }
            }
            CompoundTag once = fixed.copy();
            HistoricalBiomes.finish(fixed);
            assertEquals(once, fixed, "Another transformer's return wrapper must be harmless");
        } finally {
            active.set(null, previous);
        }
        assertArrayEquals(original, Files.readAllBytes(fixture));
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
        int index = (int) (data[cell / perLong] >>> (cell % perLong * bits) & ((1L << bits) - 1));
        return palette.getString(index);
    }
}
