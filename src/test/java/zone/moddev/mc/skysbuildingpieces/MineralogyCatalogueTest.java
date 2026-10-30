package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat;

import static org.junit.jupiter.api.Assertions.*;

class MineralogyCatalogueTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/skysbuildingpiecesmineralogy");
    private static final Path CORE = Paths.get("src/main/resources/assets/skysbuildingpieces");

    private static JsonArray read(String name) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(
                ASSETS.resolve("catalogue/" + name + ".json")), StandardCharsets.UTF_8)).getAsJsonArray();
    }

    private static Catalogue.Module register(Catalogue catalogue) throws Exception {
        return catalogue.registerModule("mineralogy", "skysbuildingpieces",
                read("materials"), MineralogyCompat.palettes(), 35);
    }

    @Test
    void optionalCatalogueAddsExactlyThirtyFiveIdsWithoutChangingVanillaSlots() throws Exception {
        Catalogue catalogue = new Catalogue();
        assertEquals(237, catalogue.palettes.size());
        Catalogue.Module rocks = register(catalogue);
        assertEquals(27, rocks.materials.size());
        assertEquals(35, rocks.palettes.size());
        assertEquals(273, catalogue.palettes.size() + 1);
        assertEquals(237, catalogue.modules.get("vanilla").palettes.size());
        assertEquals(14, rocks.palettes.stream().filter(p -> p.shape == Shape.HORIZONTAL_STEP).count());
        assertEquals(7, rocks.palettes.stream().filter(p -> p.shape == Shape.VERTICAL_STEP).count());
        assertEquals(14, rocks.palettes.stream().filter(p -> p.shape == Shape.CORNER).count());
        assertTrue(rocks.materials.values().stream().allMatch(Catalogue.MaterialDef::fullBlockStepRecipe));
    }

    @Test
    void allFiveHundredFortyStatesAndTwentyReservedValuesKeepTheirGeometryAndModels() throws Exception {
        Catalogue.Module rocks = register(new Catalogue());
        int states = 0;
        int reserved = 0;
        for (Catalogue.Palette palette : rocks.palettes) {
            JsonObject variants = object(CORE.resolve("blockstates/" + palette.id + ".json"))
                    .getAsJsonObject("variants");
            assertEquals(16, variants.entrySet().size());
            for (int metadata = 0; metadata < 16; metadata++) {
                String model = variants.getAsJsonObject("meta=" + metadata).get("model").getAsString();
                assertTrue(model.startsWith("skysbuildingpiecesmineralogy:"));
                assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/" + model.split(":")[1] + ".json")));
                if (metadata >= palette.materials.size() * palette.shape.states) {
                    final int invalid = metadata;
                    assertThrows(IllegalArgumentException.class, () -> palette.material(invalid));
                    reserved++;
                }
            }
            for (String material : palette.materials) {
                String rock = material.split(":")[1];
                for (int orientation = 0; orientation < palette.shape.states; orientation++) {
                    int metadata = palette.meta(material, orientation);
                    assertEquals(material, palette.material(metadata));
                    assertEquals(orientation, metadata % palette.shape.states);
                    JsonObject model = object(ASSETS.resolve("models/block/" + rock + "_"
                            + palette.shape.name().toLowerCase(Locale.ROOT) + "_" + orientation + ".json"));
                    assertEquals("mineralogy:blocks/" + rock, model.getAsJsonObject("textures").get("all").getAsString());
                    int mask = 0;
                    int faces = 0;
                    for (JsonElement entry : model.getAsJsonArray("elements")) {
                        JsonObject element = entry.getAsJsonObject();
                        JsonArray from = element.getAsJsonArray("from");
                        int cell = from.get(0).getAsInt() / 8 | from.get(2).getAsInt() / 8 << 1
                                | from.get(1).getAsInt() / 8 << 2;
                        mask |= 1 << cell;
                        for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                            assertFalse(face.getValue().getAsJsonObject().has("cullface"));
                            faces++;
                        }
                    }
                    assertEquals(Geometry.mask(palette.shape, orientation), mask);
                    assertEquals(palette.shape == Shape.CORNER ? 6 : 10, faces);
                    states++;
                }
            }
        }
        assertEquals(540, states);
        assertEquals(20, reserved);
    }

    @Test
    void onlyTheKnownFormerAddonIdsHaveOneToOneReplacements() throws Exception {
        Set<String> replacements = new HashSet<String>();
        for (JsonElement entry : read("palettes")) {
            String path = entry.getAsJsonObject().get("id").getAsString();
            String replacement = MineralogyCompat.replacementId("skysbuildingpiecesmineralogy:" + path);
            assertEquals("skysbuildingpieces:mineralogy_" + path, replacement);
            assertTrue(replacements.add(replacement));
        }
        assertEquals(35, replacements.size());
        assertNull(MineralogyCompat.replacementId("skysbuildingpiecesmineralogy:unknown"));
        assertNull(MineralogyCompat.replacementId("mineralogy:basalt"));
        assertNull(MineralogyCompat.replacementId("skysbuildingpieces:rock_corner_00"));
    }

    @Test
    void catalogueDataRetainsTheQualifiedMaterialAndSlotChecksums() throws Exception {
        Properties checksums = new Properties();
        try (java.io.InputStream input = Files.newInputStream(Paths.get("gradle/mineralogy-catalogue-checksums.properties"))) {
            checksums.load(input);
        }
        for (String name : new String[] {"materials", "palettes"}) {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(ASSETS.resolve("catalogue/" + name + ".json")));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02X", value & 255));
            }
            assertEquals(checksums.getProperty(name), hex.toString());
        }
    }

    @Test
    void sameNamespaceStillRejectsCollisionsOverBudgetAndLateContributions() throws Exception {
        Catalogue catalogue = new Catalogue();
        assertThrows(IllegalArgumentException.class, () -> catalogue.registerModule(
                "mineralogy", "skysbuildingpieces", read("materials"), MineralogyCompat.palettes(), 34));
        JsonArray bad = MineralogyCompat.palettes();
        bad.get(0).getAsJsonObject().addProperty("id", "rock_corner_00");
        assertThrows(IllegalArgumentException.class, () -> catalogue.registerModule(
                "mineralogy", "skysbuildingpieces", read("materials"), bad, 35));
        register(catalogue);
        assertThrows(IllegalArgumentException.class, () -> register(catalogue));
        catalogue.freeze();
        assertThrows(IllegalStateException.class, () -> catalogue.registerModule(
                "late", "late", read("materials"), MineralogyCompat.palettes(), 35));
    }

    private static JsonObject object(Path path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
