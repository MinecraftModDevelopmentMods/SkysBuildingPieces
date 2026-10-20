package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import static org.junit.jupiter.api.Assertions.*;

class WallModelTest {
    private static final Path ROOT = Paths.get("src/main/resources/assets/skysbuildingpieces/models/block");

    @Test
    void allWallsHaveRaisedPostsAndLowerNarrowArmsWithoutInternalFaces() throws Exception {
        int checked = 0;

        for (Catalogue.Palette palette : Catalogue.INSTANCE.palettes) {
            if (palette.shape != Shape.WALL) {
                continue;
            }
            for (String material : palette.materials) {
                for (int connections = 0; connections < 16; connections++) {
                    for (boolean post : new boolean[] { false, true }) {
                        String name = material.substring(material.indexOf(':') + 1) + "_wall_0_" + connections
                                + (!post && (connections == 5 || connections == 10) ? "_no_post" : "");
                        JsonObject model = json(ROOT.resolve(name + ".json"));
                        Set<String> actual = new HashSet<String>();

                        for (JsonElement entry : model.getAsJsonArray("elements")) {
                            JsonObject element = entry.getAsJsonObject();
                            int[] from = coordinates(element.getAsJsonArray("from"));
                            int[] endPoint = coordinates(element.getAsJsonArray("to"));

                            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                                int axis = axis(face.getKey());
                                int plane = positive(face.getKey()) ? endPoint[axis] : from[axis];

                                int u = axis == 0 ? 2 : 0;
                                int v = axis == 1 ? 2 : 1;

                                for (int a = from[u]; a < endPoint[u]; a++) {
                                    for (int b = from[v]; b < endPoint[v]; b++) {
                                        assertTrue(actual.add(face.getKey() + ":" + plane + ":" + a + ":" + b),
                                                name + " has overlapping faces");
                                    }
                                }
                            }
                        }

                        assertEquals(expectedSurface(connections, post), actual,
                                name + " must draw only its exterior wall surface");
                        checked++;
                    }
                }
            }
        }

        assertEquals(2304, checked);
    }

    @Test
    void wallStatesSelectPostlessModelsOnlyForStraightRuns() throws Exception {
        for (Catalogue.Palette palette : Catalogue.INSTANCE.palettes) {
            if (palette.shape != Shape.WALL) {
                continue;
            }

            JsonObject variants = json(ROOT.resolve("../../blockstates/" + palette.id + ".json").normalize())
                    .getAsJsonObject("variants");

            assertEquals(512, variants.entrySet().size());

            for (int meta = 0; meta < 16; meta++) {
                for (int connections = 0; connections < 16; connections++) {
                    for (boolean post : new boolean[] { false, true }) {
                        String key = "connections=" + connections + ",meta=" + meta + ",up=" + post;

                        assertTrue(variants.has(key), palette.id + " missing " + key);

                        String model = variants.getAsJsonObject(key).get("model").getAsString();
                        String material = palette.materials
                                .get(Math.min(meta / palette.shape.states, palette.materials.size() - 1));
                        String expected = "skysbuildingpieces:" + material.substring(material.indexOf(':') + 1)
                                + "_wall_0_" + connections
                                + (!post && (connections == 5 || connections == 10) ? "_no_post" : "");

                        assertEquals(expected, model, key + " must retain its material");
                        assertEquals(!post && (connections == 5 || connections == 10), model.endsWith("_no_post"), key);
                        assertTrue(Files.isRegularFile(ROOT.resolve(model.substring(model.indexOf(':') + 1) + ".json")),
                                model);
                    }
                }
            }
        }
    }

    @Test
    void isolatedGlassPostsKeepTheWholeTextureBorder() throws Exception {
        JsonObject model = json(ROOT.resolve("glass_wall_0_0.json"));

        for (JsonElement element : model.getAsJsonArray("elements")) {
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                assertEquals(new JsonParser().parse("[0,0,16,16]"), face.getValue().getAsJsonObject().get("uv"));
            }
        }
    }

    @Test
    void goldAndPackedIceHaveLowStraightPanelsAndRaisedPosts() throws Exception {
        for (String material : new String[] { "gold_block", "packed_ice" }) {
            for (int connections : new int[] { 5, 10 }) {
                for (boolean post : new boolean[] { false, true }) {
                    JsonObject model = json(
                            ROOT.resolve(material + "_wall_0_" + connections + (post ? "" : "_no_post") + ".json"));
                    int max = 0;

                    for (JsonElement element : model.getAsJsonArray("elements")) {
                        max = Math.max(max, element.getAsJsonObject().getAsJsonArray("to").get(1).getAsInt());
                    }

                    assertEquals(post ? 16 : 13, max, material + " must use the common wall profile");
                }
            }
        }
    }

    private static Set<String> expectedSurface(int connections, boolean post) {
        boolean[][][] filled = new boolean[16][16][16];

        if (!post && connections == 5) {
            fill(filled, 5, 0, 0, 11, 13, 16);
        } else if (!post && connections == 10) {
            fill(filled, 0, 0, 5, 16, 13, 11);
        } else {
            fill(filled, 4, 0, 4, 12, 16, 12);

            if ((connections & 1) != 0) {
                fill(filled, 5, 0, 0, 11, 13, 4);
            }
            if ((connections & 2) != 0) {
                fill(filled, 12, 0, 5, 16, 13, 11);
            }
            if ((connections & 4) != 0) {
                fill(filled, 5, 0, 12, 11, 13, 16);
            }
            if ((connections & 8) != 0) {
                fill(filled, 0, 0, 5, 4, 13, 11);
            }
        }

        Set<String> faces = new HashSet<String>();
        String[] names = { "west", "east", "down", "up", "north", "south" };
        int[][] offsets = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

        for (int blockX = 0; blockX < 16; blockX++) {
            for (int blockY = 0; blockY < 16; blockY++) {
                for (int blockZ = 0; blockZ < 16; blockZ++) {
                    if (filled[blockX][blockY][blockZ]) {
                        for (int side = 0; side < names.length; side++) {
                            int nx = blockX + offsets[side][0];
                            int ny = blockY + offsets[side][1];
                            int nz = blockZ + offsets[side][2];

                            if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16 && nz >= 0 && nz < 16 && filled[nx][ny][nz]) {
                                continue;
                            }

                            int axis = axis(names[side]);
                            int plane = (axis == 0 ? blockX : axis == 1 ? blockY : blockZ)
                                    + (positive(names[side]) ? 1 : 0);

                            faces.add(names[side] + ":" + plane + ":" + (axis == 0 ? blockZ : blockX) + ":"
                                    + (axis == 1 ? blockZ : blockY));
                        }
                    }
                }
            }
        }

        return faces;
    }

    private static void fill(boolean[][][] grid, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int blockX = x0; blockX < x1; blockX++) {
            for (int blockY = y0; blockY < y1; blockY++) {
                for (int blockZ = z0; blockZ < z1; blockZ++) {
                    grid[blockX][blockY][blockZ] = true;
                }
            }
        }
    }

    private static int axis(String face) {
        return face.equals("west") || face.equals("east") ? 0 : face.equals("up") || face.equals("down") ? 1 : 2;
    }

    private static boolean positive(String face) {
        return face.equals("east") || face.equals("up") || face.equals("south");
    }

    private static int[] coordinates(JsonArray value) {
        return new int[] { value.get(0).getAsInt(), value.get(1).getAsInt(), value.get(2).getAsInt() };
    }

    private static JsonObject json(Path path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
