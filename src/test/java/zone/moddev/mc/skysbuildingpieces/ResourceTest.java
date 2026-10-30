package zone.moddev.mc.skysbuildingpieces;

import org.junit.jupiter.api.Test;
import com.google.gson.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceTest {
    private static final Path ROOT = Paths.get("src/main/resources/assets/skysbuildingpieces");

    @Test
    void allEighteenUtf8LocalesMatch() throws Exception {
        List<String> expected = Arrays.asList("de_at", "de_au", "de_de", "en_ca", "en_en", "en_gb", "en_pt", "en_us",
                "es_es", "es_mx", "fr_ca", "fr_fr", "ja_jp", "ko_kr", "pt_br", "pt_pt", "ru_ru", "zh_cn");
        Set<String> actual = new TreeSet<String>();

        try (java.util.stream.Stream<Path> files = Files.list(ROOT.resolve("lang"))) {
            files.forEach(path -> actual.add(path.getFileName().toString().replace(".lang", "")));
        }

        assertEquals(new TreeSet<String>(expected), actual);

        List<String> keys = null;

        for (String locale : expected) {
            byte[] bytes = Files.readAllBytes(ROOT.resolve("lang/" + locale + ".lang"));
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();

            assertFalse(text.startsWith("\uFEFF"));
            assertFalse(text.contains("\r"));
            assertFalse(text.contains("\uFFFD"));
            assertTrue(text.endsWith("\n"));

            List<String> current = new ArrayList<String>();

            for (String line : text.split("\n")) {
                assertEquals(line.trim(), line);
                assertTrue(line.contains("="));

                String key = line.substring(0, line.indexOf('='));

                assertFalse(current.contains(key));
                current.add(key);
                assertTrue(line.substring(line.indexOf('=') + 1).length() > 0);
            }

            assertEquals(17 + Catalogue.INSTANCE.materials.size(), current.size());
            assertTrue(current.contains("itemGroup.skysbuildingpieces"));

            if (keys == null) {
                keys = current;
            } else {
                assertEquals(keys, current);
            }
        }

        assertArrayEquals(Files.readAllBytes(ROOT.resolve("lang/de_de.lang")),
                Files.readAllBytes(ROOT.resolve("lang/de_au.lang")));

        for (String english : Arrays.asList("en_ca", "en_en", "en_gb", "en_pt")) {
            assertArrayEquals(Files.readAllBytes(ROOT.resolve("lang/en_us.lang")),
                    Files.readAllBytes(ROOT.resolve("lang/" + english + ".lang")));
        }
    }

    @Test
    void catalogueHasCompleteModelsAndNoCulledInternalFaces() throws Exception {
        for (Catalogue.Palette palette : Catalogue.INSTANCE.palettes) {
            JsonObject blockstate = json(ROOT.resolve("blockstates/" + palette.id + ".json"));

            assertTrue(blockstate.has("variants"));

            for (Map.Entry<String, JsonElement> entry : blockstate.getAsJsonObject("variants").entrySet()) {
                String name = entry.getValue().getAsJsonObject().get("model").getAsString()
                        .substring("skysbuildingpieces:".length());

                assertTrue(Files.isRegularFile(ROOT.resolve("models/block/" + name + ".json")), name);
            }
            for (String m : palette.materials) {
                assertTrue(Files.isRegularFile(ROOT.resolve("models/item/" + m.substring(10) + "_"
                        + palette.shape.name().toLowerCase(Locale.ROOT) + ".json")));
            }
        }
        try (java.util.stream.Stream<Path> models = Files.list(ROOT.resolve("models/block"))) {
            for (Path model : (Iterable<Path>) models::iterator) {
                JsonObject data = json(model);

                assertTrue(data.has("textures"));

                for (JsonElement element : data.getAsJsonArray("elements")) {
                    for (Map.Entry<String, JsonElement> f : element.getAsJsonObject().getAsJsonObject("faces")
                            .entrySet()) {
                        if (f.getValue().getAsJsonObject().has("cullface")) {
                            String file = model.getFileName().toString();
                            String face = f.getKey();

                            assertTrue(file.contains("_wall_"), file);
                            assertEquals(face, f.getValue().getAsJsonObject().get("cullface").getAsString());

                            int axis = face.equals("east") || face.equals("west") ? 0 : 2;
                            boolean positive = face.equals("east") || face.equals("south");

                            assertEquals(positive ? 16 : 0, element.getAsJsonObject()
                                    .getAsJsonArray(positive ? "to" : "from").get(axis).getAsInt(), file);
                            assertFalse(face.equals("up") || face.equals("down"), file);
                        }
                        if (model.getFileName().toString().contains("_snow")) {
                            assertFalse(f.getValue().getAsJsonObject().has("tintindex"));
                        }
                    }
                }
            }
        }
    }

    @Test
    void retiredTemplateModelsRemainAvailableForOldSaves() throws Exception {
        Set<String> models = new HashSet<String>();

        for (String t : Arrays.asList("slab", "vertical_slab", "step", "corner", "stairs", "wall", "pane")) {
            assertTrue(models.add(new String(Files.readAllBytes(ROOT.resolve("models/item/template_" + t + ".json")),
                    StandardCharsets.UTF_8)));
        }
    }

    @Test
    void bottomGrassAndSnowPiecesKeepTheirSideFringe() throws Exception {
        for (String shape : Arrays.asList("step", "corner")) {
            for (int orientation = 4; orientation < 8; orientation++) {
                for (String weather : Arrays.asList("dry", "snow")) {
                    String name = "grass_" + shape + "_" + orientation + "_" + weather;
                    JsonObject model = json(ROOT.resolve("models/block/" + name + ".json"));

                    for (JsonElement element : model.getAsJsonArray("elements")) {
                        JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                        for (String side : Arrays.asList("north", "south", "west", "east")) {
                            if (!faces.has(side)) {
                                continue;
                            }
                            JsonArray uv = faces.getAsJsonObject(side).getAsJsonArray("uv");
                            assertEquals(0, uv.get(1).getAsInt(), name + " / " + side + " fringe");
                            assertEquals(8, uv.get(3).getAsInt(), name + " / " + side + " pixel scale");
                        }
                    }
                }
            }
        }
    }

    private static JsonObject json(Path path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
