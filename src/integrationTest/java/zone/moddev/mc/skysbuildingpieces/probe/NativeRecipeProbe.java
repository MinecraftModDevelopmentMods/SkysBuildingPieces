package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

final class NativeRecipeProbe {
    private NativeRecipeProbe() {
    }

    static ResourceKey<Recipe<?>> key(Identifier id) {
        return ResourceKey.create(Registries.RECIPE, id);
    }

    static List<Ingredient> ingredients(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getIngredients().stream().map(cell -> cell.orElse(null)).toList();
        }
        return recipe.placementInfo().ingredients();
    }

    static ItemStack example(Ingredient ingredient) {
        return ingredient.items().map(holder -> new ItemStack(holder.value()))
                .findFirst().orElseThrow(() -> new AssertionError("No item for " + ingredient));
    }

    static List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> matching(
            net.minecraft.world.item.crafting.CraftingInput grid, net.minecraft.server.level.ServerLevel level) {
        return level.getServer().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe
                        && recipe.matches(grid, level))
                .map(holder -> new net.minecraft.world.item.crafting.RecipeHolder<>(
                        holder.id(), (net.minecraft.world.item.crafting.CraftingRecipe) holder.value()))
                .toList();
    }

    static ItemStack result(Recipe<?> recipe, Level level) {
        var displays = recipe.display();
        return displays.isEmpty() ? ItemStack.EMPTY
                : displays.getFirst().result().resolveForFirstStack(SlotDisplayContext.fromLevel(level));
    }
}
