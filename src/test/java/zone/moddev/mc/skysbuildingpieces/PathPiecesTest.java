package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;

import static org.junit.jupiter.api.Assertions.*;

class PathPiecesTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/skysbuildingpieces");

    @Test
    void pathPalettesAreAppendedWithoutTakingOverLegacyContent() {
        Catalogue catalogue = new Catalogue();
        assertTrue(catalogue.materials.containsKey("minecraft:grass_path"), "Path pieces are missing");
        assertEquals("dirt_vertical_slab_00", catalogue.palettes.get(234).id);
        assertEquals("path_step_00", catalogue.palettes.get(235).id);
        assertEquals(Shape.STEP, catalogue.palettes.get(235).shape);
        assertEquals("path_corner_00", catalogue.palettes.get(236).id);
        assertEquals(Shape.CORNER, catalogue.palettes.get(236).shape);
        assertEquals("minecraft:dirt", catalogue.materials.get("minecraft:grass_path").normalDrop());
        assertFalse(LegacyMapping.allowedMaterial("minecraft:grass_path", Shape.CORNER));
    }

    @Test
    void pathModelsKeepTheFootprintAndLowerOnlyTheExposedTop() throws Exception {
        for (String shape : new String[] { "step", "corner" }) {
            int orientations = shape.equals("step") ? 12 : 8;
            for (int orientation = 0; orientation < orientations; orientation++) {
                JsonObject source = read("models/block/grass_" + shape + "_" + orientation + "_dry.json");
                JsonObject path = read("models/block/grass_path_" + shape + "_" + orientation + ".json");
                assertEquals("minecraft:blocks/grass_path_top", path.getAsJsonObject("textures").get("top").getAsString());
                double originalTop = top(source);
                assertEquals(originalTop - 1, top(path));
                int sourceIndex = 0;
                for (JsonElement entry : path.getAsJsonArray("elements")) {
                    JsonObject element = entry.getAsJsonObject();
                    JsonObject original = source.getAsJsonArray("elements").get(sourceIndex).getAsJsonObject();
                    sourceIndex += 2; // Grass overlays are separate elements, not part of the path.
                    assertEquals(original.getAsJsonArray("from"), element.getAsJsonArray("from"));
                    assertEquals(original.getAsJsonArray("to").get(0), element.getAsJsonArray("to").get(0));
                    assertEquals(original.getAsJsonArray("to").get(2), element.getAsJsonArray("to").get(2));
                    for (java.util.Map.Entry<String, JsonElement> entryFace : element.getAsJsonObject("faces").entrySet()) {
                        JsonObject face = entryFace.getValue().getAsJsonObject();
                        assertFalse(face.has("tintindex"));
                        assertFalse(face.has("cullface"));
                        assertNotEquals("#overlay", face.get("texture").getAsString());
                    }
                }
            }
        }
    }

    @Test
    void loweredSupportModelsCloseTheGapWithoutChangingTheWalkingSurface() throws Exception {
        for (String shape : new String[] { "step", "corner" }) {
            int orientations = shape.equals("step") ? 12 : 8;
            JsonObject variants = read("blockstates/path_" + shape + "_00.json").getAsJsonObject("variants");
            for (int meta = 0; meta < 16; meta++) {
                assertTrue(variants.has("lowered_support=false,meta=" + meta));
                assertTrue(variants.has("lowered_support=true,meta=" + meta));
            }
            for (int orientation = 0; orientation < orientations; orientation++) {
                String model = "models/block/grass_path_" + shape + "_" + orientation;
                JsonObject ordinary = read(model + ".json");
                JsonObject lowered = read(model + "_lowered.json");
                assertEquals(top(ordinary), top(lowered));
                for (int cell = 0; cell < ordinary.getAsJsonArray("elements").size(); cell++) {
                    JsonObject original = ordinary.getAsJsonArray("elements").get(cell).getAsJsonObject();
                    JsonObject extended = lowered.getAsJsonArray("elements").get(cell).getAsJsonObject();
                    double base = original.getAsJsonArray("from").get(1).getAsDouble();
                    assertEquals(base == 0 ? -1 : base, extended.getAsJsonArray("from").get(1).getAsDouble());
                    assertEquals(original.getAsJsonArray("to"), extended.getAsJsonArray("to"));
                    assertEquals(original.getAsJsonArray("from").get(0), extended.getAsJsonArray("from").get(0));
                    assertEquals(original.getAsJsonArray("from").get(2), extended.getAsJsonArray("from").get(2));
                }
            }
        }
    }

    private static double top(JsonObject model) {
        double highest = 0;
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            highest = Math.max(highest, entry.getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble());
        }
        return highest;
    }

    private static JsonObject read(String relative) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(ASSETS.resolve(relative)),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
