package zone.moddev.mc.skysbuildingpieces.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** Compare loaded recipes after optional ingredients and material choices have been resolved. */
public final class RecipeUniquenessChecks {
    private static final Set<String> SKY_MODS = Set.of("skysgrassslabs", "skysbuildingpieces",
            "skysbuildingpiecesbop", "skysterrainsmoother", "skysterrainsmootherbop");

    public static void run(ServerLevel world) {
        var registries = RegistryOps.create(JsonOps.INSTANCE, world.registryAccess());
        Map<String, List<JsonObject>> groups = new LinkedHashMap<>();
        JsonArray definitions = new JsonArray();
        for (var holder : world.getRecipeManager().getRecipes().stream()
                .sorted(java.util.Comparator.comparing(row -> row.id().toString())).toList()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) {
                continue;
            }
            JsonObject encoded = Recipe.CODEC.encodeStart(registries, recipe).getOrThrow().getAsJsonObject();
            List<String> ingredients = new ArrayList<>();
            boolean plainIngredients = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == Ingredient.EMPTY) {
                    ingredients.add("");
                    continue;
                }
                JsonElement encodedIngredient = Ingredient.CODEC.encodeStart(registries, ingredient).getOrThrow();
                if (!plain(encodedIngredient)) {
                    plainIngredients = false;
                    break;
                }
                TreeSet<String> accepted = new TreeSet<>();
                for (ItemStack stack : ingredient.getItems()) {
                    accepted.add(ItemStack.CODEC.encodeStart(registries, stack).getOrThrow().toString());
                }
                ingredients.add(accepted.toString());
            }
            if (!plainIngredients) {
                continue;
            }

            JsonObject signature = new JsonObject();
            boolean single = ingredients.stream().filter(value -> !value.isEmpty()).count() == 1;
            if (recipe instanceof ShapedRecipe shaped && !single) {
                int width = shaped.getWidth();
                List<String> reflected = new ArrayList<>();
                for (int row = 0; row < shaped.getHeight(); row++) {
                    for (int column = width - 1; column >= 0; column--) {
                        reflected.add(ingredients.get(row * width + column));
                    }
                }
                String normal = ingredients.toString();
                String mirror = reflected.toString();
                signature.addProperty("kind", "shaped");
                signature.addProperty("width", width);
                signature.addProperty("height", shaped.getHeight());
                signature.addProperty("ingredients", normal.compareTo(mirror) <= 0 ? normal : mirror);
            } else {
                signature.addProperty("kind", "shapeless");
                signature.addProperty("ingredients", ingredients.stream().filter(value -> !value.isEmpty())
                        .sorted().toList().toString());
            }
            signature.add("result", ItemStack.CODEC.encodeStart(registries,
                    recipe.getResultItem(world.registryAccess())).getOrThrow());
            signature.addProperty("preserve_components",
                    encoded.has("preserve_tag") && encoded.get("preserve_tag").getAsBoolean());

            JsonObject definition = new JsonObject();
            definition.addProperty("id", holder.id().toString());
            definition.addProperty("sky", SKY_MODS.contains(holder.id().getNamespace()));
            definition.add("signature", signature);
            definition.add("recipe", encoded);
            definitions.add(definition);
            groups.computeIfAbsent(signature.toString(), ignored -> new ArrayList<>()).add(definition);
        }

        JsonArray duplicates = new JsonArray();
        for (List<JsonObject> group : groups.values()) {
            if (group.size() > 1 && group.stream().anyMatch(row -> row.get("sky").getAsBoolean())) {
                JsonArray entries = new JsonArray();
                group.forEach(entries::add);
                duplicates.add(entries);
            }
        }
        try {
            var json = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(Path.of("recipe-definitions.json"), json.toJson(definitions) + "\n", StandardCharsets.UTF_8);
            Files.writeString(Path.of("recipe-duplicates.json"), json.toJson(duplicates) + "\n", StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not record the recipe comparison", failure);
        }
        if (!duplicates.isEmpty()) {
            throw new AssertionError("The loaded recipe book has " + duplicates.size()
                    + " duplicate crafting groups involving Sky recipes; see recipe-duplicates.json");
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "SKY_RECIPE_UNIQUENESS_PASS ordinary_recipes=" + definitions.size() + " duplicate_groups=0");
    }

    private static boolean plain(JsonElement ingredient) {
        if (ingredient.isJsonArray()) {
            for (JsonElement option : ingredient.getAsJsonArray()) {
                if (!plain(option)) {
                    return false;
                }
            }
            return true;
        }
        return ingredient.isJsonObject() && (ingredient.getAsJsonObject().has("item")
                || ingredient.getAsJsonObject().has("tag"));
    }

    private RecipeUniquenessChecks() {
    }
}

