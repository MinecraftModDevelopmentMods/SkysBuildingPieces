package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.datafix.fixes.CavesAndCliffsRenames;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently checks the raw saved content behind the narrow log profiles. */
class NativeRegistryNoticeProofTest {
    @Test
    void permittedRegistryNamesHaveCompleteSavedContentProof() throws Exception {
        MinecraftTestBootstrap.registerVanilla();
        JsonObject profiles = new JsonParser().parse(new String(
                Files.readAllBytes(Paths.get("tools/native-log-profiles.json")), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("profiles");
        assertEquals(7, profiles.size());
        Set<String> versions = new HashSet<>();

        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : profiles.entrySet()) {
            JsonObject profile = entry.getValue().getAsJsonObject();
            Set<String> names = new HashSet<>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> registry
                    : profile.getAsJsonObject("mappings").entrySet()) {
                // Biomes are used terrain data. Check their complete source
                // volumes below, separately from genuinely unused entries.
                if (registry.getKey().equals("minecraft:worldgen/biome")) {
                    JsonObject binding = profile.getAsJsonObject("static_biome_contract");
                    byte[] raw = Files.readAllBytes(Paths.get(binding.get("path").getAsString()));
                    assertEquals(binding.get("sha256").getAsString(), checksum(raw));
                    JsonObject proof = new JsonParser().parse(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                    assertEquals(profile.get("sha256"), proof.get("source_world_sha256"));
                    assertEquals(registry.getValue(), proof.get("snapshot_entries"));
                    assertEquals(profile.getAsJsonObject("native_biome_contract").get("sha256"),
                            proof.get("native_biome_contract_sha256"));
                    continue;
                }
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> mapping
                        : registry.getValue().getAsJsonObject().entrySet()) {
                    names.add(mapping.getKey());
                }
            }
            verifyUnusedRegistryProof(profile, names);
            GrassRenameProof grass = new GrassRenameProof(profile);
            assertFalse(names.isEmpty());
            assertEquals(0, profile.get("non_biome_saved_uses").getAsInt());
            BiomeProof biomes = new BiomeProof(profile);
            assertTrue(versions.add(biomes.version));
            assertEquals(entry.getKey(), checksum(Files.readAllBytes(Paths.get(profile.get("fixture").getAsString()))));
            int records = 0;

            try (ZipFile archive = new ZipFile(profile.get("fixture").getAsString())) {
                java.util.Enumeration<? extends ZipEntry> members = archive.entries();
                while (members.hasMoreElements()) {
                    ZipEntry member = members.nextElement();
                    String label = member.getName();
                    try (InputStream input = archive.getInputStream(member)) {
                        if (label.endsWith(".dat") || label.endsWith(".dat_old")) {
                            CompoundTag root = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                            // Registry availability is not a saved block, entity or inventory use.
                            root.remove("FML");
                            root.remove("fml");
                            inspect(root, names, label);
                            biomes.inspect(root);
                            records++;
                        } else if (label.endsWith(".mca")) {
                            records += region(readAll(input), names, label, biomes, grass);
                        }
                    }
                }
            }
            assertEquals(profile.get("inspected_nbt_records").getAsInt(), records);
            biomes.complete(profile);
            grass.complete();
        }
        assertEquals(Set.of("1.14.4", "1.15.2", "1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.1"), versions);
    }

    private static void verifyUnusedRegistryProof(JsonObject profile, Set<String> names) throws Exception {
        boolean forge50 = profile.has("forge50_unused_registry_contract");
        JsonObject binding = profile.getAsJsonObject(forge50
                ? "forge50_unused_registry_contract" : "forge47_unused_registry_contract");
        if (binding == null) {
            return;
        }

        byte[] raw = Files.readAllBytes(Paths.get(binding.get("path").getAsString()));
        assertEquals(binding.get("sha256").getAsString(), checksum(raw));
        JsonObject proof = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(profile.get("sha256"), proof.get("source_world_sha256"));
        JsonObject snapshot = proof.getAsJsonObject("snapshot_entries");
        JsonObject mappings = profile.getAsJsonObject("mappings");
        Set<String> provenNames = new HashSet<>();
        for (var registry : snapshot.entrySet()) {
            assertEquals(mappings.get(registry.getKey()), registry.getValue());
            provenNames.addAll(registry.getValue().getAsJsonObject().keySet());
        }
        JsonObject savedUses = proof.getAsJsonObject("saved_uses");
        assertTrue(names.containsAll(provenNames));
        assertFalse(provenNames.isEmpty());
        assertEquals(provenNames, savedUses.keySet());
        savedUses.entrySet().forEach(entry -> assertEquals(0, entry.getValue().getAsInt(), entry.getKey()));
    }

    private static int region(byte[] bytes, Set<String> names, String label, BiomeProof biomes,
            GrassRenameProof grass) throws Exception {
        if (bytes.length == 0) {
            return 0;
        }
        assertTrue(bytes.length >= 8192, label);
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int records = 0;
        for (int slot = 0; slot < 1024; slot++) {
            int location = header.getInt(slot * 4);
            if (location == 0) {
                continue;
            }
            int offset = (location >>> 8) * 4096;
            int length = header.getInt(offset);
            int compression = bytes[offset + 4] & 255;
            InputStream payload = new ByteArrayInputStream(bytes, offset + 5, length - 1);
            if (compression == 1) {
                payload = new GZIPInputStream(payload);
            } else if (compression == 2) {
                payload = new InflaterInputStream(payload);
            } else {
                assertEquals(3, compression, "Unexpected fixture compression: " + label);
            }
            try (DataInputStream input = new DataInputStream(payload)) {
                CompoundTag root = NbtIo.read(input);
                grass.inspect(root);
                inspect(root, names, label + ":" + slot);
                biomes.inspect(root);
            }
            records++;
        }
        return records;
    }

    /** Match every old grass block in the raw fixture before checking other saved uses. */
    private static final class GrassRenameProof {
        private final Set<net.minecraft.core.BlockPos> expected = new HashSet<>();
        private final Set<net.minecraft.core.BlockPos> actual = new HashSet<>();
        private final Map<net.minecraft.core.BlockPos, Integer> expectedTicks = new HashMap<>();
        private final Map<net.minecraft.core.BlockPos, Integer> actualTicks = new HashMap<>();
        private final Map<Integer, Set<CompoundTag>> fixedPaletteEntries = new HashMap<>();

        GrassRenameProof(JsonObject profile) throws Exception {
            JsonObject binding = profile.getAsJsonObject("native_block_rename_contract");
            if (binding == null) {
                return;
            }
            byte[] raw = Files.readAllBytes(Paths.get(binding.get("path").getAsString()));
            assertEquals(binding.get("sha256").getAsString(), checksum(raw));
            JsonObject proof = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(profile.get("sha256"), proof.get("source_world_sha256"));
            assertEquals("minecraft:grass", proof.get("source").getAsString());
            assertEquals("minecraft:short_grass", proof.get("target").getAsString());
            for (var entry : proof.getAsJsonArray("positions")) {
                var coordinates = entry.getAsJsonArray();
                assertTrue(expected.add(new net.minecraft.core.BlockPos(
                        coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt())));
            }
            assertEquals(binding.get("checked_positions").getAsInt(), expected.size());
            if (proof.has("legacy_ticks")) {
                for (var entry : proof.getAsJsonArray("legacy_ticks")) {
                    var coordinates = entry.getAsJsonArray();
                    var position = new net.minecraft.core.BlockPos(
                            coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt());
                    assertTrue(expected.contains(position));
                    expectedTicks.merge(position, 1, Integer::sum);
                }
            }
        }

        boolean enabled() {
            return !expected.isEmpty();
        }

        void inspect(CompoundTag root) {
            if (!enabled()) {
                return;
            }
            boolean legacyLayout = root.contains("Level", Tag.TAG_COMPOUND);
            CompoundTag chunk = legacyLayout ? root.getCompound("Level") : root;
            int chunkX = chunk.getInt("xPos");
            int chunkZ = chunk.getInt("zPos");
            ListTag sections = chunk.getList(legacyLayout ? "Sections" : "sections", Tag.TAG_COMPOUND);
            for (int index = 0; index < sections.size(); index++) {
                CompoundTag section = sections.getCompound(index);
                CompoundTag states = legacyLayout ? section : section.getCompound("block_states");
                ListTag palette = states.getList(legacyLayout ? "Palette" : "palette", Tag.TAG_COMPOUND);
                for (int state = 0; state < palette.size(); state++) {
                    CompoundTag entry = palette.getCompound(state);
                    if (!entry.getString("Name").equals("minecraft:grass")) {
                        continue;
                    }
                    verifyNativeRename(root.getInt("DataVersion"), entry);
                    int bits = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
                    int perWord = Long.SIZE / bits;
                    long[] packed = states.getLongArray(legacyLayout ? "BlockStates" : "data");
                    for (int cell = 0; cell < 4096; cell++) {
                        long paletteIndex = palette.size() == 1 ? 0 : paletteIndex(packed, bits, perWord, cell);
                        if (paletteIndex == state) {
                            assertTrue(actual.add(new net.minecraft.core.BlockPos(
                                    chunkX * 16 + cell % 16, section.getInt("Y") * 16 + cell / 256,
                                    chunkZ * 16 + cell / 16 % 16)));
                        }
                    }
                    // Exclude only this checked palette entry from the unused-item scan.
                    entry.putString("Name", "minecraft:short_grass");
                }
            }
            if (legacyLayout) {
                ListTag ticks = chunk.getList("TileTicks", Tag.TAG_COMPOUND);
                for (int index = 0; index < ticks.size(); index++) {
                    CompoundTag tick = ticks.getCompound(index);
                    if (tick.getString("i").equals("minecraft:grass")) {
                        var position = new net.minecraft.core.BlockPos(tick.getInt("x"), tick.getInt("y"), tick.getInt("z"));
                        actualTicks.merge(position, 1, Integer::sum);
                        // The matching historical block tick is not a stored grass item.
                        tick.putString("i", "minecraft:short_grass");
                    }
                }
            }
        }

        private void verifyNativeRename(int version, CompoundTag entry) {
            if (!fixedPaletteEntries.computeIfAbsent(version, ignored -> new HashSet<>()).add(entry.copy())) {
                return;
            }
            int current = net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            assertTrue(version > 0 && version < current);
            var source = new com.mojang.serialization.Dynamic<net.minecraft.nbt.Tag>(net.minecraft.nbt.NbtOps.INSTANCE, entry);
            var fixed = net.minecraft.util.datafix.DataFixers.getDataFixer().update(
                    net.minecraft.util.datafix.fixes.References.BLOCK_STATE, source, version, current);
            assertEquals("minecraft:short_grass", fixed.get("Name").asString(""));
        }

        private static long paletteIndex(long[] packed, int bits, int perWord, int cell) {
            long mask = (1L << bits) - 1;
            if (packed.length == (4096 + perWord - 1) / perWord) {
                return (packed[cell / perWord] >>> ((cell % perWord) * bits)) & mask;
            }
            // Pre-1.16 palettes can span two words for one entry.
            assertEquals((4096 * bits + Long.SIZE - 1) / Long.SIZE, packed.length);
            int start = cell * bits;
            int word = start / Long.SIZE;
            int offset = start % Long.SIZE;
            long value = packed[word] >>> offset;
            if (offset + bits > Long.SIZE) {
                value |= packed[word + 1] << (Long.SIZE - offset);
            }
            return value & mask;
        }

        void complete() {
            assertEquals(expected, actual);
            assertEquals(expectedTicks, actualTicks);
        }
    }

    private static String checksum(byte[] data) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder text = new StringBuilder();
        for (byte value : digest) {
            text.append(String.format("%02X", value & 255));
        }
        return text.toString();
    }

    /** The runtime also checks these same cells after vanilla's data fixing. */
    private static final class BiomeProof {
        private final JsonObject contract;
        private final JsonObject names;
        private final Map<String, byte[]> volumes = new HashMap<>();
        private final Set<String> visited = new HashSet<>();
        private final String version;
        private final boolean nativeHeight;
        private long cells;
        private long renamed;

        BiomeProof(JsonObject profile) throws Exception {
            JsonObject proof = profile.getAsJsonObject("native_biome_contract");
            byte[] bytes = Files.readAllBytes(Paths.get(proof.get("path").getAsString()));
            assertEquals(proof.get("sha256").getAsString(), checksum(bytes));
            contract = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            String format = contract.get("format").getAsString();
            nativeHeight = format.equals("zlib-u8-quart-volume-v2");
            assertTrue(nativeHeight || format.equals("zlib-u8-quart-volume-v1"));
            if (nativeHeight) {
                assertEquals(-16, contract.get("min_quart_y").getAsInt());
                assertEquals(96, contract.get("quart_height").getAsInt());
            }
            assertEquals(profile.get("sha256"), contract.get("source_world_sha256"));
            assertEquals(proof.get("source"), contract.get("source_minecraft"));
            version = proof.get("source").getAsString();
            names = contract.getAsJsonObject("source_names");
            for (com.google.gson.JsonElement value : contract.getAsJsonArray("chunks")) {
                JsonObject row = value.getAsJsonObject();
                String position = row.get("x").getAsInt() + "," + row.get("z").getAsInt();
                try (InputStream packed = new InflaterInputStream(new ByteArrayInputStream(
                        Base64.getDecoder().decode(row.get("cells").getAsString())))) {
                    byte[] volume = readAll(packed);
                    assertEquals(nativeHeight ? 1536 : 1024, volume.length, position);
                    assertNull(volumes.put(position, volume), "Repeated source chunk " + position);
                }
            }
        }

        void inspect(CompoundTag root) {
            if (!(nativeHeight ? root.contains("sections", 9) : root.contains("Level", 10))) {
                return;
            }
            CompoundTag level = nativeHeight ? root : root.getCompound("Level");
            String status = level.getString("Status");
            if (!status.equals("full") && !status.equals("minecraft:full")) {
                return;
            }
            String position = level.getInt("xPos") + "," + level.getInt("zPos");
            byte[] expected = volumes.get(position);
            assertNotNull(expected, "Missing biome contract for " + position);
            assertTrue(visited.add(position), "Repeated saved chunk " + position);
            if (nativeHeight) {
                inspectNativeSections(level, expected, position);
                return;
            }
            int[] saved;
            if (level.contains("Biomes", 7)) {
                byte[] legacy = level.getByteArray("Biomes");
                saved = new int[legacy.length];
                for (int column = 0; column < legacy.length; column++) {
                    saved[column] = Byte.toUnsignedInt(legacy[column]);
                }
            } else {
                assertTrue(level.contains("Biomes", 11), position);
                saved = level.getIntArray("Biomes");
            }
            assertTrue(saved.length == 256 || saved.length == 1024, position);
            for (int cell = 0; cell < expected.length; cell++) {
                int source = saved.length == 1024 ? saved[cell]
                        : saved[((cell >> 2 & 3) * 4 + 2) * 16 + (cell & 3) * 4 + 2];
                assertEquals(source, Byte.toUnsignedInt(expected[cell]), position + " cell=" + cell);
                assertTrue(names.has(Integer.toString(source)), position);
                String oldName = names.get(Integer.toString(source)).getAsString();
                String current = CavesAndCliffsRenames.RENAMES.getOrDefault(oldName, oldName);
                if (current.equals("minecraft:deep_warm_ocean")) {
                    current = "minecraft:warm_ocean";
                }
                renamed += oldName.equals(current) ? 0 : 1;
                cells++;
            }
        }

        private void inspectNativeSections(CompoundTag level, byte[] expected, String position) {
            Map<Integer, CompoundTag> sections = new HashMap<>();
            for (Tag entry : level.getList("sections", 10)) {
                CompoundTag section = (CompoundTag) entry;
                assertNull(sections.put(section.getInt("Y"), section), "Repeated source section");
            }
            for (int cell = 0; cell < expected.length; cell++) {
                CompoundTag section = sections.get(-4 + cell / 64);
                assertNotNull(section, position + " cell=" + cell);
                CompoundTag container = section.getCompound("biomes");
                ListTag palette = container.getList("palette", 8);
                assertFalse(palette.isEmpty());
                int paletteIndex = 0;
                if (palette.size() > 1) {
                    int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
                    int valuesPerWord = 64 / bits;
                    int localCell = cell & 63;
                    long word = container.getLongArray("data")[localCell / valuesPerWord];
                    paletteIndex = (int) (word >>> ((localCell % valuesPerWord) * bits) & ((1L << bits) - 1));
                }
                String source = palette.getString(paletteIndex);
                String recorded = names.get(Integer.toString(Byte.toUnsignedInt(expected[cell]))).getAsString();
                assertEquals(recorded, source, position + " cell=" + cell);
                String current = CavesAndCliffsRenames.RENAMES.getOrDefault(source, source);
                renamed += source.equals(current) ? 0 : 1;
                cells++;
            }
        }

        void complete(JsonObject profile) {
            JsonObject proof = profile.getAsJsonObject("native_biome_contract");
            assertEquals(volumes.keySet(), visited);
            assertEquals(proof.get("checked_cells").getAsLong(), cells);
            assertEquals(contract.get("checked_cells").getAsLong(), cells);
            assertEquals(proof.get("renamed_cells").getAsLong(), renamed);
            assertEquals(contract.get("native_renamed_cells").getAsLong(), renamed);
            assertEquals(profile.get("saved_uses").getAsLong(), renamed);
        }
    }

    private static void inspect(Tag value, Set<String> names, String label) {
        if (value instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) value;
            for (String key : compound.getAllKeys()) {
                assertFalse(names.contains(key), "Saved registry-name key in " + label + ": " + key);
                inspect(compound.get(key), names, label + "/" + key);
            }
        } else if (value instanceof ListTag) {
            for (Tag entry : (ListTag) value) {
                inspect(entry, names, label);
            }
        } else if (value instanceof StringTag) {
            assertFalse(names.contains(value.getAsString()), "Saved registry-name value in " + label);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
