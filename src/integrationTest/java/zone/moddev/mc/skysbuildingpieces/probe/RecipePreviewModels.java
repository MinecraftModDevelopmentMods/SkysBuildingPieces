package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/** Checks every ingredient picture the recipe book can cycle through. */
final class RecipePreviewModels {
    private static final Set<String> OWNERS = new HashSet<>(Arrays.asList(
            "skysbuildingpieces", "skysbuildingpiecesbop", "skysgrassslabs",
            "skysterrainsmoother", "skysterrainsmootherbop"));

    static void check(Minecraft minecraft) {
        int recipes = 0;
        int previews = 0;
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ResourceLocation id = recipe.getRegistryName();
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
        System.out.println("SKY_RECIPE_PREVIEW_MODELS_PASS recipes=" + recipes + " previews=" + previews);
    }

    private static void checkModel(Minecraft minecraft, ItemStack stack, ResourceLocation recipe) {
        if (stack.isEmpty()) {
            throw new AssertionError("Empty recipe preview: " + recipe);
        }
        String label = recipe + " -> " + stack.getItem().getRegistryName() + ":" + stack.getMetadata();
        IBakedModel model = minecraft.getRenderItem().getItemModelWithOverrides(stack, null, null);
        IBakedModel missing = minecraft.getBlockRendererDispatcher().getBlockModelShapes()
                .getModelManager().getMissingModel();
        if (model == missing || model.getParticleTexture().getIconName().contains("missingno")) {
            throw new AssertionError("Missing recipe preview model: " + label);
        }
        for (EnumFacing face : new EnumFacing[] {null, EnumFacing.DOWN, EnumFacing.UP,
                EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST}) {
            for (BakedQuad quad : model.getQuads(null, face, 0)) {
                if (quad.getSprite().getIconName().contains("missingno")) {
                    throw new AssertionError("Missing recipe preview texture: " + label);
                }
            }
        }
    }

    private RecipePreviewModels() {
    }
}
