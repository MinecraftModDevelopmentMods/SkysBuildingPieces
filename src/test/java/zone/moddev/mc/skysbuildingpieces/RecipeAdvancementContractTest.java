package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeAdvancementContractTest {
    @Test
    void everyNativeRecipeHasMatchingVisibleBookIngredientsAndConditionalUnlocks() throws Exception {
        Path root = Paths.get("src/main/resources/data/skysbuildingpieces");
        int checked = 0;

        try (java.util.stream.Stream<Path> files = Files.walk(root.resolve("recipe"))) {
            for (Path path : (Iterable<Path>) files.filter(file -> file.toString().endsWith(".json"))::iterator) {
                JsonObject recipe = read(path);

                if (recipe.get("type").getAsString().equals("skysbuildingpieces:legacy_piece")) {
                    continue;
                }

                String relative = root.resolve("recipe").relativize(path).toString().replace('\\', '/');
                String id = "skysbuildingpieces:" + relative.substring(0, relative.length() - 5);
                JsonObject advancement = read(root.resolve("advancement/recipes").resolve(relative));

                assertFalse(advancement.has("parent"), "Large recipe catalogues have independent hidden unlocks");
                assertEquals(id, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
                assertFalse(recipe.has("forge:condition"));
                assertFalse(advancement.has("forge:condition"));
                assertEquals(recipe.get("neoforge:conditions"), advancement.get("neoforge:conditions"));
                if (recipe.has("neoforge:conditions")) {
                    assertTrue(recipe.get("neoforge:conditions").isJsonArray());
                }

                JsonObject criteria = advancement.getAsJsonObject("criteria");

                assertEquals(id, criteria.getAsJsonObject("has_the_recipe").getAsJsonObject("conditions").get("recipe")
                        .getAsString());

                String input = recipe.getAsJsonObject("key").getAsJsonObject("X").get("item").getAsString();

                assertEquals(input, criteria.getAsJsonObject("has_ingredient").getAsJsonObject("conditions")
                        .getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsString());
                assertEquals(1, advancement.getAsJsonArray("requirements").size());
                assertEquals(criteria.size(),
                        advancement.getAsJsonArray("requirements").get(0).getAsJsonArray().size());
                assertTrue(recipe.getAsJsonObject("result").get("count").getAsInt() > 0);

                for (Map.Entry<String, JsonElement> key : recipe.getAsJsonObject("key").entrySet()) {
                    assertFalse(key.getValue().getAsJsonObject().has("data"), "Flat item identity replaces metadata");
                    assertFalse(key.getValue().getAsJsonObject().has("nbt"));
                }

                checked++;
            }
        }

        assertTrue(checked > 1400, "Every catalogue cut and recombination needs a native unlock");
    }

    private static JsonObject read(Path path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
