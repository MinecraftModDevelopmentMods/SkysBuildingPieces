package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForwardFixtureContractTest {
    @Test
    void native117FixtureKeepsTheAcceptedSourceAndCompleteSavedPieces() throws Exception {
        Path root = Paths.get("src/testFixtures/worlds");
        JsonObject manifest = new JsonParser().parse(Files.readString(root.resolve("building-pieces-1.17.1.json")))
                .getAsJsonObject();
        byte[] archive = Files.readAllBytes(root.resolve("building-pieces-1.17.1.zip"));
        assertEquals("b8d7fac567f6dc22240186cd0b0601eb1dfe2090", manifest.get("source_commit").getAsString());
        assertEquals("40ACD56C59F50C9C5DC484781B7FEA180E1C45FAB5470735CA27983E14258240",
                manifest.get("source_jar_sha256").getAsString());
        assertEquals("E7EC1B7CD05A744B241C135AA47211326B218FD30F32D0A818748B5B03C89A67", sha256(archive));
        assertEquals(7029, manifest.getAsJsonArray("states").size());
        assertEquals(3346, manifest.get("wet_states").getAsInt());
        assertEquals(144, manifest.get("snowy_states").getAsInt());
        assertEquals(7029, manifest.get("source_saved_states_verified").getAsInt());
        assertTrue(manifest.get("source_unchanged").getAsBoolean());
        assertTrue(manifest.get("player_shovel").getAsString().contains("Enchantments"));
        assertTrue(manifest.get("player_nested").getAsString().contains("Items"));
        assertTrue(verifyMembers(manifest, archive).contains("data/skysbuildingpieces_world_state.dat"));
    }

    @Test
    void native116FixtureKeepsAllPiecesAndTheRenamedVanillaPath() throws Exception {
        Path root = Paths.get("src/testFixtures/worlds");
        JsonObject manifest = new JsonParser().parse(Files.readString(root.resolve("building-pieces-1.16.5.json")))
                .getAsJsonObject();
        byte[] archive = Files.readAllBytes(root.resolve("building-pieces-1.16.5.zip"));

        assertEquals("7842655cc4c06b4556207c0fba7ec7ba7c802289", manifest.get("source_commit").getAsString());
        assertEquals("B4DC9F16E5CF42902B93667B9E95EC846B66E2A3C488CC7CF48D9FBA3B477C41",
                manifest.get("source_jar_sha256").getAsString());
        assertEquals("B712B235C72BCB3D09B41DAE7A79F6B18FF0A8E08E08F774BD050E341E526C9F", sha256(archive));
        assertEquals(7029, manifest.getAsJsonArray("states").size());
        assertEquals(7029, manifest.get("source_saved_states_verified").getAsInt());
        assertEquals(3346, manifest.get("wet_states").getAsInt());
        assertEquals(144, manifest.get("snowy_states").getAsInt());
        assertTrue(manifest.get("source_unchanged").getAsBoolean());
        assertTrue(manifest.get("player_shovel").getAsString().contains("Enchantments"));
        assertTrue(manifest.get("player_nested").getAsString().contains("Items"));
        assertEquals(1, manifest.getAsJsonArray("entities").size());

        boolean nativePath = false;
        for (com.google.gson.JsonElement value : manifest.getAsJsonArray("states")) {
            JsonObject state = value.getAsJsonObject();
            if (state.get("x").getAsInt() == 832 && state.get("y").getAsInt() == 160
                    && state.get("z").getAsInt() == 512) {
                assertTrue(state.get("state").getAsString().contains("minecraft:grass_path"));
                nativePath = true;
            }
        }
        assertTrue(nativePath, "The source needs a real vanilla path to exercise its native rename");
        Set<String> members = verifyMembers(manifest, archive);
        assertTrue(members.contains("playerdata/" + manifest.get("player").getAsString() + ".dat"));
        assertTrue(members.contains("data/skysbuildingpieces_world_state.dat"));
    }

    @Test
    void native115FixtureKeepsEveryStateAndBothPlayerInventories() throws Exception {
        Path root = Paths.get("src/testFixtures/worlds");
        JsonObject manifest = new JsonParser().parse(new String(
                Files.readAllBytes(root.resolve("building-pieces-1.15.2.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        byte[] archive = Files.readAllBytes(root.resolve("building-pieces-1.15.2.zip"));

        assertEquals("named", manifest.get("kind").getAsString());
        assertEquals("c5573be98e7b148e54b1fcaa15b8a6eb402391cc", manifest.get("source_commit").getAsString());
        assertEquals("94A5BAA4B3DC025A338A60BAC27B3E5BBAD4AE0CE494BA5EC1F852E401A704F7",
                manifest.get("source_jar_sha256").getAsString());
        assertEquals("03A1DD5E363661D3D71194AC18CB1B53484841BC98A57A93E9056EC5048C5525", sha256(archive));
        assertEquals(7028, manifest.getAsJsonArray("states").size());
        assertEquals(3346, manifest.get("wet_states").getAsInt());
        assertEquals(144, manifest.get("snowy_states").getAsInt());
        assertEquals(7028, manifest.get("source_saved_states_verified").getAsInt());
        assertTrue(manifest.get("source_unchanged").getAsBoolean());
        assertTrue(manifest.get("player_shovel").getAsString().contains("Enchantments"));
        assertTrue(manifest.get("player_nested").getAsString().contains("Items"));
        Set<String> members = verifyMembers(manifest, archive);
        assertTrue(members.contains("playerdata/" + manifest.get("player").getAsString() + ".dat"));
        assertTrue(members.contains("data/skysbuildingpieces_world_state.dat"));
    }

    @Test
    void native114FixtureLocksWetSnowyStatesAndRichInventoryData() throws Exception {
        Path root = Paths.get("src/testFixtures/worlds");
        JsonObject manifest = new JsonParser().parse(new String(
                Files.readAllBytes(root.resolve("building-pieces-1.14.4.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        byte[] archive = Files.readAllBytes(root.resolve("building-pieces-1.14.4.zip"));

        assertEquals("named", manifest.get("kind").getAsString());
        assertEquals("65dde80079b7096d7b4de5f22e8bdc64051bbaf7", manifest.get("source_commit").getAsString());
        assertEquals("8A0A1DEA2A4A6C069519C7B1BCF28331186FF2DF6284DB72C2FFDB8178FC84D4",
                manifest.get("source_jar_sha256").getAsString());
        assertEquals("D4D261CC385C4E2C7249F1D72FE6C4479D9D98B0208EFC93A1FDBAB78EFBC209", sha256(archive));
        assertEquals(7028, manifest.getAsJsonArray("states").size());
        assertEquals(3346, manifest.get("wet_states").getAsInt());
        assertEquals(144, manifest.get("snowy_states").getAsInt());
        assertEquals(7028, manifest.get("source_saved_states_verified").getAsInt());
        assertTrue(manifest.get("source_unchanged").getAsBoolean());
        assertEquals(1, manifest.getAsJsonArray("containers").size());
        assertEquals(1, manifest.getAsJsonArray("entities").size());
        assertTrue(verifyMembers(manifest, archive).contains("data/skysbuildingpieces_world_state.dat"));
    }

    @Test
    void delayedFixtureKeepsItsOriginalProvenanceAndUnopenedContent() throws Exception {
        Path root = Paths.get("src/testFixtures/worlds");
        JsonObject manifest = new JsonParser()
                .parse(new String(Files.readAllBytes(root.resolve("buildingbricks-delayed-1.10.2.json")),
                        StandardCharsets.UTF_8))
                .getAsJsonObject();
        byte[] archive = Files.readAllBytes(root.resolve("buildingbricks-delayed-1.10.2.zip"));

        assertEquals("975C3D03F335EC7D64C35B84A4810AB77F4FF55E710EA85620EE765E50311CC5", sha256(archive));
        assertEquals("8D5BA8583465AEA76403475C71A3AA558DA6AB9AF4576C5C858B98330EF5ADB0",
                manifest.get("derived_from_archive_sha256").getAsString());
        assertEquals(52, manifest.getAsJsonArray("states").size());
        assertEquals(26, java.util.stream.StreamSupport.stream(manifest.getAsJsonArray("states").spliterator(), false)
                .filter(value -> value.getAsJsonObject().has("delayed")).count());
        verifyMembers(manifest, archive);
    }

    @Test
    void originalBuildingBricksFixtureKeepsAllStepCodesAndSourceRegistry() throws Exception {
        Path root = Paths.get("src/testFixtures/worlds");
        JsonObject manifest = new JsonParser()
                .parse(new String(Files.readAllBytes(root.resolve("buildingbricks-pieces-1.10.2.json")),
                        StandardCharsets.UTF_8))
                .getAsJsonObject();
        byte[] archive = Files.readAllBytes(root.resolve("buildingbricks-pieces-1.10.2.zip"));

        assertEquals("8D5BA8583465AEA76403475C71A3AA558DA6AB9AF4576C5C858B98330EF5ADB0", sha256(archive));
        assertEquals(26, manifest.getAsJsonArray("states").size());

        Set<Integer> stepCodes = new HashSet<>();

        manifest.getAsJsonArray("states").forEach(value -> {
            JsonObject saved = value.getAsJsonObject();

            if (saved.get("id").getAsString().equals("buildingbricks:rock_step")) {
                stepCodes.add(saved.get("meta").getAsInt());
            }
        });
        assertEquals(java.util.stream.IntStream.range(0, 16).boxed().collect(java.util.stream.Collectors.toSet()),
                stepCodes);
        assertEquals(1, manifest.getAsJsonArray("containers").size());
        assertEquals(1, manifest.getAsJsonArray("entities").size());
        verifyMembers(manifest, archive);
    }

    @Test
    void accepted112FixtureAndEveryWorldFileStayChecksumLocked() throws Exception {
        Path root = Paths.get("src/testFixtures/worlds");
        JsonObject manifest = new JsonParser().parse(
                new String(Files.readAllBytes(root.resolve("building-pieces-1.12.2.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        byte[] archive = Files.readAllBytes(root.resolve("building-pieces-1.12.2.zip"));

        assertEquals("af3ce9b73d215a2f9e27a11324da56a752e4edac", manifest.get("source_commit").getAsString());
        assertEquals("4273DD260D2F259F4FCE56680DA79AA9B2FBF90FD3ABCC1B494E228965FE1316",
                manifest.get("source_jar_sha256").getAsString());
        assertEquals(3434, manifest.getAsJsonArray("states").size());

        Set<String> seen = verifyMembers(manifest, archive);

        assertTrue(seen.contains("data/skysbuildingpieces_world_state.dat"));
    }

    private Set<String> verifyMembers(JsonObject manifest, byte[] archive) throws Exception {
        assertEquals(manifest.get("archive_sha256").getAsString(), sha256(archive));

        Set<String> seen = new HashSet<>();

        try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            ZipEntry entry;

            while ((entry = input.getNextEntry()) != null) {
                assertTrue(seen.add(entry.getName()), "Duplicate fixture member");
                assertFalse(entry.getName().startsWith("/") || entry.getName().contains(".."));

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;

                while ((count = input.read(buffer)) >= 0) {
                    bytes.write(buffer, 0, count);
                }

                assertEquals(manifest.getAsJsonObject("files").get(entry.getName()).getAsString(),
                        sha256(bytes.toByteArray()), entry.getName());
            }
        }

        Set<String> expected = manifest.getAsJsonObject("files").entrySet().stream().map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(expected, seen);
        assertTrue(seen.contains("level.dat"));
        return seen;
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();

        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02X", value & 255));
        }

        return result.toString();
    }
}
