package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** Compare the recipes actually loaded after material choices and optional ingredients are resolved. */
public final class RecipeUniquenessChecks {
    private static final Set<String> SKY = Set.of("skysgrassslabs", "skysbuildingpieces",
            "skysbuildingpiecesbop", "skysterrainsmoother", "skysterrainsmootherbop");

    public static void run(ServerLevel world) {
        Map<String, List<String>> signatures = new LinkedHashMap<>();
        for (var holder : world.getServer().getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) {
                continue;
            }
            List<String> ingredients = NativeRecipeProbe.ingredients(recipe).stream().map(ingredient ->
                    ingredient == null ? "" : ingredient.items().map(item ->
                            BuiltInRegistries.ITEM.getKey(item.value()).toString()).sorted().toList().toString()).toList();
            String key;
            if (recipe instanceof ShapedRecipe shaped && ingredients.stream().filter(value -> !value.isEmpty()).count() > 1) {
                List<String> reflected = new ArrayList<>();
                for (int row = 0; row < shaped.getHeight(); row++) {
                    for (int column = shaped.getWidth() - 1; column >= 0; column--) {
                        reflected.add(ingredients.get(row * shaped.getWidth() + column));
                    }
                }
                String normal = ingredients.toString();
                String mirror = reflected.toString();
                key = "shaped/" + shaped.getWidth() + "/" + shaped.getHeight() + "/"
                        + (normal.compareTo(mirror) <= 0 ? normal : mirror);
            } else {
                key = "shapeless/" + ingredients.stream().filter(value -> !value.isEmpty()).sorted().toList();
            }
            var output = NativeRecipeProbe.result(recipe, world);
            key += "/" + BuiltInRegistries.ITEM.getKey(output.getItem()) + "/" + output.getCount()
                    + "/" + output.getComponentsPatch();
            signatures.computeIfAbsent(key, ignored -> new ArrayList<>()).add(holder.id().identifier().toString());
        }
        var duplicates = signatures.values().stream().filter(ids -> ids.size() > 1
                && ids.stream().anyMatch(id -> SKY.contains(id.split(":", 2)[0]))).toList();
        if (!duplicates.isEmpty()) {
            throw new AssertionError("Duplicate loaded Sky crafting recipes: " + duplicates);
        }
        org.apache.logging.log4j.LogManager.getLogger().info("SKY_RECIPE_UNIQUENESS_PASS signatures={}", signatures.size());
    }

    private RecipeUniquenessChecks() {
    }
}
