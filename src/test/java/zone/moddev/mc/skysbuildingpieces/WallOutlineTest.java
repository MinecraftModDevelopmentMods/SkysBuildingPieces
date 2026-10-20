package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.content.WallGeometry;
import static org.junit.jupiter.api.Assertions.*;

class WallOutlineTest {
    @Test
    void selectionEdgesMatchModelCreasesWithoutEnclosingEmptySpace() throws Exception {
        for (int connections = 0; connections < 16; connections++) {
            for (boolean post : new boolean[] { false, true }) {
                String name = "glass_wall_0_" + connections
                        + (!post && (connections == 5 || connections == 10) ? "_no_post" : "");
                Path path = Paths.get("src/main/resources/assets/skysbuildingpieces/models/block/" + name + ".json");
                JsonObject model = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Map<String, Set<String>> normals = new HashMap<String, Set<String>>();

                for (JsonElement entry : model.getAsJsonArray("elements")) {
                    JsonObject element = entry.getAsJsonObject();
                    int[] from = point(element.getAsJsonArray("from"));
                    int[] endPoint = point(element.getAsJsonArray("to"));

                    for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                        String normal = face.getKey();
                        int axis = normal.equals("east") || normal.equals("west") ? 0
                                : normal.equals("up") || normal.equals("down") ? 1 : 2;
                        int u = axis == 0 ? 2 : 0;
                        int v = axis == 1 ? 2 : 1;

                        int plane = normal.equals("east") || normal.equals("up") || normal.equals("south")
                                ? endPoint[axis]
                                : from[axis];

                        for (int a = from[u]; a < endPoint[u]; a++) {
                            for (int b = from[v]; b < endPoint[v]; b++) {
                                int[] coordinates = new int[3];

                                coordinates[axis] = plane;
                                coordinates[u] = a;
                                coordinates[v] = b;
                                normal(normals, key(u, coordinates), face.getKey());
                                normal(normals, key(v, coordinates), face.getKey());
                                coordinates[v]++;
                                normal(normals, key(u, coordinates), face.getKey());
                                coordinates[v]--;
                                coordinates[u]++;
                                normal(normals, key(v, coordinates), face.getKey());
                            }
                        }
                    }
                }

                Set<String> expected = new HashSet<String>();

                for (Map.Entry<String, Set<String>> edge : normals.entrySet()) {
                    if (edge.getValue().size() > 1) {
                        expected.add(edge.getKey());
                    }
                }

                Set<String> actual = new HashSet<String>();

                for (WallGeometry.Edge edge : WallGeometry.outline(connections, post)) {
                    int[] from = { edge.x0, edge.y0, edge.z0 };
                    int[] endPoint = { edge.x1, edge.y1, edge.z1 };

                    int axis = from[0] != endPoint[0] ? 0 : from[1] != endPoint[1] ? 1 : 2;

                    for (int entryIndex = 0; entryIndex < 3; entryIndex++) {
                        assertTrue(from[entryIndex] >= 0 && endPoint[entryIndex] <= 16);

                        if (entryIndex != axis) {
                            assertEquals(from[entryIndex], endPoint[entryIndex]);
                        }
                    }

                    assertTrue(endPoint[axis] > from[axis]);

                    for (int t = from[axis]; t < endPoint[axis]; t++) {
                        int[] coordinates = from.clone();

                        coordinates[axis] = t;
                        assertTrue(actual.add(key(axis, coordinates)), "duplicate outline edge");
                    }
                }

                assertEquals(expected, actual, name + " selection must follow the actual model");
            }
        }

        assertEquals(12, WallGeometry.outline(0, true).size());
        assertEquals(12, WallGeometry.outline(5, false).size());
        assertEquals(12, WallGeometry.outline(10, false).size());
    }

    @Test
    void cachedOutlinesCannotBeChanged() {
        assertSame(WallGeometry.outline(3, true), WallGeometry.outline(3, true));
        assertThrows(UnsupportedOperationException.class, () -> WallGeometry.outline(3, true).clear());
        assertThrows(IllegalArgumentException.class, () -> WallGeometry.outline(16, true));
    }

    private static void normal(Map<String, Set<String>> edges, String key, String normal) {
        Set<String> existing = edges.get(key);

        if (existing == null) {
            existing = new HashSet<String>();
            edges.put(key, existing);
        }

        existing.add(normal);
    }

    private static String key(int axis, int[] coordinates) {
        return axis + ":" + coordinates[0] + ":" + coordinates[1] + ":" + coordinates[2];
    }

    private static int[] point(JsonArray a) {
        return new int[] { a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt() };
    }
}
