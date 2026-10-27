package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Explicit repair tool for soil edges; ordinary builds never rewrite source models. */
public final class AlignSoilModels {
    public static void main(String[] arguments) throws Exception {
        Path root = Paths.get("src/main/resources/assets/skysbuildingpieces/models/block");
        int changed = 0;

        try (java.util.stream.Stream<Path> files = Files.list(root)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                JsonObject model = new JsonParser().parse(new String(Files.readAllBytes(file),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                if (!model.getAsJsonObject("textures").has("side")) {
                    continue;
                }
                String side = model.getAsJsonObject("textures").get("side").getAsString();
                if (!side.endsWith("/grass_side") && !side.endsWith("/grass_side_snowed")) {
                    continue;
                }

                boolean updated = false;
                for (JsonElement value : model.getAsJsonArray("elements")) {
                    JsonObject element = value.getAsJsonObject();
                    JsonArray from = element.getAsJsonArray("from");
                    JsonArray to = element.getAsJsonArray("to");
                    int columnTop = to.get(1).getAsInt();

                    // Full-height columns retain a continuous texture across their two cells.
                    for (JsonElement otherValue : model.getAsJsonArray("elements")) {
                        JsonObject other = otherValue.getAsJsonObject();
                        JsonArray otherFrom = other.getAsJsonArray("from");
                        JsonArray otherTo = other.getAsJsonArray("to");
                        if (otherFrom.get(0).equals(from.get(0)) && otherTo.get(0).equals(to.get(0))
                                && otherFrom.get(2).equals(from.get(2)) && otherTo.get(2).equals(to.get(2))) {
                            columnTop = Math.max(columnTop, otherTo.get(1).getAsInt());
                        }
                    }

                    JsonObject faces = element.getAsJsonObject("faces");
                    for (String direction : Arrays.asList("north", "south", "west", "east")) {
                        if (!faces.has(direction)) {
                            continue;
                        }
                        JsonArray uv = faces.getAsJsonObject(direction).getAsJsonArray("uv");
                        int firstRow = columnTop - to.get(1).getAsInt();
                        int lastRow = columnTop - from.get(1).getAsInt();
                        if (uv.get(1).getAsInt() != firstRow || uv.get(3).getAsInt() != lastRow) {
                            JsonArray aligned = new JsonArray();
                            aligned.add(uv.get(0));
                            aligned.add(new JsonPrimitive(firstRow));
                            aligned.add(uv.get(2));
                            aligned.add(new JsonPrimitive(lastRow));
                            faces.getAsJsonObject(direction).add("uv", aligned);
                            updated = true;
                        }
                    }
                }
                if (updated) {
                    Files.write(file, (new Gson().toJson(model) + "\n").getBytes(StandardCharsets.UTF_8));
                    changed++;
                }
            }
        }
        System.out.println("SOIL_EDGE_MODELS_ALIGNED files=" + changed);
    }

    private AlignSoilModels() {
    }
}
