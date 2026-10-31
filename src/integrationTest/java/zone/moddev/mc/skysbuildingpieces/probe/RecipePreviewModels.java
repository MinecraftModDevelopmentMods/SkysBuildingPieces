package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.item.ItemStack;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;

/** Checks every ingredient picture the recipe book can cycle through. */
final class RecipePreviewModels {
    private static final Set<String> OWNERS = new HashSet<>(Arrays.asList("skysbuildingpieces", "skysbuildingpiecesbop",
            "skysgrassslabs", "skysterrainsmoother", "skysterrainsmootherbop"));

    static void check(Minecraft minecraft) {
        int recipes = 0;
        int previews = 0;

        for (IRecipe<?> recipe : minecraft.getConnection().getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();

            if (id == null || !OWNERS.contains(id.getNamespace()) || recipe.isDynamic()) {
                continue;
            }
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack preview : ingredient.getMatchingStacks()) {
                    checkModel(minecraft, preview, id);
                    previews++;
                }
            }

            checkModel(minecraft, recipe.getRecipeOutput(), id);
            previews++;
            recipes++;
        }
        if (recipes < 1000) {
            throw new AssertionError("Client did not receive the complete native recipe catalogue: " + recipes);
        }

        System.out.println("SKY_RECIPE_PREVIEW_MODELS_PASS recipes=" + recipes + " previews=" + previews);
    }

    private static void checkModel(Minecraft minecraft, ItemStack stack, ResourceLocation recipe) {
        if (stack.isEmpty()) {
            throw new AssertionError("Empty recipe preview: " + recipe);
        }

        String label = recipe + " -> " + stack.getItem().getRegistryName();
        IBakedModel model = minecraft.getItemRenderer().getItemModelWithOverrides(stack, null, null);
        IBakedModel missing = minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                .getMissingModel();

        if (model == missing || model.getParticleTexture().getName().toString().contains("missingno")) {
            throw new AssertionError("Missing recipe preview model: " + label);
        }
        for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST }) {
            for (BakedQuad quad : model.getQuads(null, face, new java.util.Random(0))) {
                if (quad.getSprite().getName().toString().contains("missingno")) {
                    throw new AssertionError("Missing recipe preview texture: " + label);
                }
            }
        }
    }

    private RecipePreviewModels() {
    }
}
