package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import zone.moddev.mc.skysbuildingpieces.content.WallGeometry;

/** Explicit resource-generation tool; normal builds never rewrite source assets. */
public final class GenerateWallModels {
    private static Path ROOT = Paths.get("src/main/resources/assets/skysbuildingpieces/models/block");
    private static final String[] FACES = { "west", "east", "down", "up", "north", "south" };

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0 && arguments.length != 2) {
            throw new IllegalArgumentException("Usage: GenerateWallModels [namespace asset-root]");
        }

        String namespace = arguments.length == 0 ? "skysbuildingpieces" : arguments[0];
        Path assets = arguments.length == 0 ? Paths.get("src/main/resources/assets/skysbuildingpieces")
                : Paths.get(arguments[1]);

        ROOT = assets.resolve("models/block");

        JsonArray palettes = new JsonParser().parse(
                new String(Files.readAllBytes(assets.resolve("catalogue/palettes.json")), StandardCharsets.UTF_8))
                .getAsJsonArray();
        int written = 0;

        for (JsonElement entry : palettes) {
            JsonObject palette = entry.getAsJsonObject();

            if (!palette.get("shape").getAsString().equals("wall")) {
                continue;
            }

            boolean glass = palette.get("group").getAsString().contains("glass");
            JsonArray materials = palette.getAsJsonArray("materials");

            for (JsonElement materialEntry : materials) {
                String material = materialEntry.getAsString();

                for (int connections = 0; connections < 16; connections++) {
                    for (boolean post : new boolean[] { true, false }) {
                        if (!post && connections != 5 && connections != 10) {
                            continue;
                        }

                        Path path = ROOT.resolve(name(material, connections, post) + ".json");
                        JsonObject previous = new JsonParser().parse(new String(
                                Files.readAllBytes(ROOT.resolve(name(material, connections, true) + ".json")),
                                StandardCharsets.UTF_8)).getAsJsonObject();
                        JsonObject model = new JsonObject();

                        model.addProperty("parent", "block/block");

                        if (glass) {
                            model.addProperty("ambientocclusion", false);
                        } else if (previous.has("ambientocclusion")) {
                            model.add("ambientocclusion", previous.get("ambientocclusion"));
                        }

                        model.add("textures", previous.get("textures"));
                        model.add("elements", exterior(connections, post, glass));
                        Files.write(path, (model.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                        written++;
                    }
                }
            }

            JsonObject variants = new JsonObject();

            for (int meta = 0; meta < 16; meta++) {
                for (int connections = 0; connections < 16; connections++) {
                    for (boolean post : new boolean[] { false, true }) {
                        String material = materials.get(Math.min(meta, materials.size() - 1)).getAsString();
                        JsonObject variant = new JsonObject();

                        variant.addProperty("model", namespace + ":" + name(material, connections, post));
                        variants.add("connections=" + connections + ",meta=" + meta + ",up=" + post, variant);
                    }
                }
            }

            JsonObject blockstate = new JsonObject();

            blockstate.add("variants", variants);
            Files.write(assets.resolve("blockstates/" + palette.get("id").getAsString() + ".json"),
                    (blockstate.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        }

        System.out.println("Regenerated " + written + " wall models for " + namespace);
    }

    private static String name(String material, int connections, boolean post) {
        return material.substring(material.indexOf(':') + 1) + "_wall_0_" + connections
                + (!post && (connections == 5 || connections == 10) ? "_no_post" : "");
    }

    private static JsonArray exterior(int connections, boolean post, boolean glass) {
        boolean[][][] filled = new boolean[16][16][16];

        for (WallGeometry.Bounds bounds : WallGeometry.boxes(connections, post)) {
            for (int blockX = bounds.minX; blockX < bounds.maxX; blockX++) {
                for (int blockY = bounds.minY; blockY < bounds.maxY; blockY++) {
                    for (int blockZ = bounds.minZ; blockZ < bounds.maxZ; blockZ++) {
                        filled[blockX][blockY][blockZ] = true;
                    }
                }
            }
        }

        JsonArray result = new JsonArray();

        for (int side = 0; side < FACES.length; side++) {
            for (int plane = 0; plane <= 16; plane++) {
                int axis = side / 2;
                boolean positive = (side & 1) != 0;
                boolean[][] surface = new boolean[16][16];

                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        surface[u][v] = occupied(filled, axis, positive ? plane - 1 : plane, u, v)
                                && !occupied(filled, axis, positive ? plane : plane - 1, u, v);
                    }
                }
                for (int v = 0; v < 16; v++) {
                    for (int u = 0; u < 16; u++) {
                        if (surface[u][v]) {
                            int width = 1;
                            int height = 1;

                            while (u + width < 16 && surface[u + width][v]) {
                                width++;
                            }

                            rows:
                            while (v + height < 16) {
                                for (int blockX = u; blockX < u + width; blockX++) {
                                    if (!surface[blockX][v + height]) {
                                        break rows;
                                    }
                                }

                                height++;
                            }

                            for (int blockX = u; blockX < u + width; blockX++) {
                                for (int blockY = v; blockY < v + height; blockY++) {
                                    surface[blockX][blockY] = false;
                                }
                            }

                            result.add(quad(FACES[side], axis, plane, u, v, u + width, v + height, glass));
                        }
                    }
                }
            }
        }

        return result;
    }

    private static boolean occupied(boolean[][][] filled, int axis, int cell, int u, int v) {
        if (cell < 0 || cell >= 16) {
            return false;
        }

        return axis == 0 ? filled[cell][v][u] : axis == 1 ? filled[u][cell][v] : filled[u][v][cell];
    }

    private static JsonObject quad(String side, int axis, int plane, int u0, int v0, int u1, int v1, boolean glass) {
        int[] from = new int[3];
        int[] endPoint = new int[3];

        from[axis] = plane;
        endPoint[axis] = plane;

        int u = axis == 0 ? 2 : 0;
        int v = axis == 1 ? 2 : 1;

        from[u] = u0;
        from[v] = v0;
        endPoint[u] = u1;
        endPoint[v] = v1;

        JsonObject element = new JsonObject();

        element.add("from", array(from));
        element.add("to", array(endPoint));

        JsonObject face = new JsonObject();

        face.add("uv", textureUv(side, from, endPoint, glass));
        face.addProperty("texture", side.equals("up") ? "#top" : side.equals("down") ? "#bottom" : "#side");

        boolean positive = side.equals("east") || side.equals("up") || side.equals("south");

        if (axis != 1 && plane == (positive ? 16 : 0)) {
            face.addProperty("cullface", side);
        }

        JsonObject faces = new JsonObject();

        faces.add(side, face);
        element.add("faces", faces);
        return element;
    }

    private static JsonArray textureUv(String side, int[] from, int[] endPoint, boolean glass) {
        // Glass keeps its framed sprite. Other materials retain native pixel scale.
        if (glass) {
            return array(0, 0, 16, 16);
        }
        if (side.equals("down")) {
            return array(from[0], 16 - endPoint[2], endPoint[0], 16 - from[2]);
        }
        if (side.equals("up")) {
            return array(from[0], from[2], endPoint[0], endPoint[2]);
        }
        if (side.equals("north")) {
            return array(16 - endPoint[0], 16 - endPoint[1], 16 - from[0], 16 - from[1]);
        }
        if (side.equals("south")) {
            return array(from[0], 16 - endPoint[1], endPoint[0], 16 - from[1]);
        }
        if (side.equals("west")) {
            return array(from[2], 16 - endPoint[1], endPoint[2], 16 - from[1]);
        }

        return array(16 - endPoint[2], 16 - endPoint[1], 16 - from[2], 16 - from[1]);
    }

    private static JsonArray array(int... values) {
        JsonArray result = new JsonArray();

        for (int value : values) {
            result.add(new JsonPrimitive(value));
        }

        return result;
    }

    private GenerateWallModels() {
    }
}
