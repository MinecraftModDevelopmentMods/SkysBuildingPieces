package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Checks every ingredient picture the recipe book can cycle through. */
final class RecipePreviewModels {
    private static final Set<String> OWNERS = new HashSet<>(Arrays.asList("skysbuildingpieces", "skysbuildingpiecesbop",
            "skysgrassslabs", "skysterrainsmoother", "skysterrainsmootherbop"));

    static void check(Minecraft minecraft) {
        int recipes = 0;
        int previews = 0;

        for (Recipe<?> recipe : minecraft.getConnection().getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();

            if (id == null || !OWNERS.contains(id.getNamespace()) || recipe.isSpecial()) {
                continue;
            }
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack preview : ingredient.getItems()) {
                    checkModel(minecraft, preview, id);
                    previews++;
                }
            }

            checkModel(minecraft, recipe.getResultItem(), id);
            previews++;
            recipes++;
        }
        if (recipes < 1000) {
            throw new AssertionError("Client did not receive the complete native recipe catalogue: " + recipes);
        }

        org.apache.logging.log4j.LogManager.getLogger().info("SKY_RECIPE_PREVIEW_MODELS_PASS recipes=" + recipes + " previews=" + previews);
    }

    private static void checkModel(Minecraft minecraft, ItemStack stack, ResourceLocation recipe) {
        if (stack.isEmpty()) {
            throw new AssertionError("Empty recipe preview: " + recipe);
        }

        String label = recipe + " -> " + stack.getItem().getRegistryName();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, null, null, 0);
        BakedModel missing = minecraft.getBlockRenderer().getBlockModelShaper().getModelManager()
                .getMissingModel();

        if (model == missing || model.getParticleIcon().getName().toString().contains("missingno")) {
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
