package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeAdvancementContractTest {
    @Test
    void everyBookEntryRewardsItsRecipeAndUsesAlternativeUnlockCriteria() throws Exception {
        JsonArray entries = new JsonParser().parse(read(Paths.get("src/main/recipe-book/recipes.json"))).getAsJsonArray();
        assertTrue(entries.size() > 0);
        Set<String> recipes = new HashSet<>();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            String recipe = entry.get("recipe").getAsString();
            assertTrue(recipes.add(recipe), recipe);
            assertEquals("skysbuildingpieces", entry.get("owner").getAsString());
            Path path = Paths.get("build/resources/main/assets/skysbuildingpieces/advancements/recipes",
                    recipe.split(":", 2)[1] + ".json");
            JsonObject advancement = new JsonParser().parse(read(path)).getAsJsonObject();
            assertFalse(advancement.has("parent"),
                    "Large recipe catalogues must not share a recursive visibility parent: " + recipe);
            assertEquals(recipe, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
            JsonObject criteria = advancement.getAsJsonObject("criteria");
            assertEquals("minecraft:recipe_unlocked", criteria.getAsJsonObject("has_the_recipe").get("trigger").getAsString());
            JsonArray requirements = advancement.getAsJsonArray("requirements");
            assertEquals(1, requirements.size(), "Unlock criteria must be alternatives: " + recipe);
            assertEquals(criteria.size(), requirements.get(0).getAsJsonArray().size());
            for (Map.Entry<String, JsonElement> criterion : criteria.entrySet()) {
                if (criterion.getKey().equals("has_the_recipe")) continue;
                assertEquals("minecraft:inventory_changed", criterion.getValue().getAsJsonObject().get("trigger").getAsString());
            }
        }
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
