package zone.moddev.mc.skysbuildingpieces.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.oredict.RecipeSorter;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Ordinary, material-specific shaped recipes. Native recipes remain untouched. */
public final class CuttingRecipe extends ShapedRecipes {
    private static final List<CuttingRecipe> REGISTERED = new ArrayList<CuttingRecipe>();
    public final String material;
    public final Shape shape;
    public final CraftingPattern pattern;
    public final ItemStack ingredient;

    private CuttingRecipe(String material, Shape shape, CraftingPattern pattern, ItemStack ingredient,
            ItemStack output) {
        super("skysbuildingpieces", pattern.width(), pattern.height(), inputs(pattern, ingredient), output);
        this.material = material;
        this.shape = shape;
        this.pattern = pattern;
        this.ingredient = ingredient.copy();
    }

    private static NonNullList<Ingredient> inputs(CraftingPattern pattern, ItemStack ingredient) {
        NonNullList<Ingredient> result = NonNullList.withSize(pattern.width() * pattern.height(), Ingredient.EMPTY);

        for (int row = 0; row < pattern.height(); row++) {
            for (int column = 0; column < pattern.width(); column++) {
                if (pattern.occupied(column, row)) {
                    result.set(column + row * pattern.width(), Ingredient.fromStacks(ingredient.copy()));
                }
            }
        }

        return result;
    }

    public static List<CuttingRecipe> recipes() {
        return Collections.unmodifiableList(REGISTERED);
    }

    @Override
    public ItemStack getCraftingResult(net.minecraft.inventory.InventoryCrafting grid) {
        ItemStack result = super.getCraftingResult(grid);

        if (pattern == CraftingPattern.ROTATE && shape == Shape.SLAB) {
            for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
                ItemStack input = grid.getStackInSlot(slot);

                if (!input.isEmpty() && input.hasTagCompound()) {
                    result.setTagCompound(input.getTagCompound().copy());
                    break;
                }
            }
        }

        return result;
    }

    public static void register(IForgeRegistry<IRecipe> registry) {
        currentRegistry = registry;
        RecipeSorter.register("skysbuildingpieces:cutting", CuttingRecipe.class, RecipeSorter.Category.SHAPED,
                "after:minecraft:shaped");

        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            ItemStack full = Pieces.stack(Pieces.nativeState(material, "full"), 1);
            boolean half = full == null;

            if (half) {
                full = Pieces.stack(Pieces.nativeState(material, "slab"), 1);
            }
            if (full == null) {
                throw new IllegalStateException("No obtainable crafting material: " + material.id);
            }

            // Keep native row recipes (including snow layers and wildcard decorative cuts). A diagonal pair provides
            // the missing slab variants.
            if (material.block("slab") == null) {
                add(material, Shape.SLAB,
                        CraftingPattern.rotateOnlySlab(material.id) ? CraftingPattern.STEP : CraftingPattern.SLAB, full,
                        half);
            }

            ItemStack horizontal = Pieces.stack(Pieces.state(material.id, Shape.SLAB, 1), 1);
            ItemStack vertical = Pieces.stack(Pieces.state(material.id, Shape.VERTICAL_SLAB, 0), 1);

            if (horizontal == null && vertical != null && !(material.id.equals("minecraft:dirt")
                    && net.minecraft.item.Item.getByNameOrId("skysgrassslabs:dirt_slab") != null)) {
                add(material, Shape.VERTICAL_SLAB, CraftingPattern.VERTICAL_SLAB, full, half);
            }
            // Cutting half blocks avoids native full-block diagonal recipes such as BOP's bamboo recovery. Diagonals
            // also avoid vanilla's two-slab chiseled sandstone and stone-brick recipes.
            if (horizontal != null) {
                add(material, Shape.HORIZONTAL_STEP, CraftingPattern.STEP, horizontal, false);
            }
            if (vertical != null) {
                add(material, Shape.HORIZONTAL_STEP, CraftingPattern.STEP, vertical, false);
            }
            if (material.soil()) {
                add(material, Shape.HORIZONTAL_STEP, CraftingPattern.SOIL_STEP, full, half);
            }

            ItemStack step = Pieces.stack(Pieces.state(material.id, Shape.HORIZONTAL_STEP, 0), 1);

            if (step != null) {
                add(material, Shape.CORNER, CraftingPattern.CORNER, step, false);
            }

            ItemStack verticalStep = Pieces.stack(Pieces.state(material.id, Shape.VERTICAL_STEP, 0), 1);

            if (step != null && verticalStep != null && !ItemStack.areItemsEqual(step, verticalStep)) {
                add(material, Shape.CORNER, CraftingPattern.CORNER, verticalStep, false);
                register(material.id, Shape.VERTICAL_STEP, CraftingPattern.ROTATE, step, verticalStep);
                register(material.id, Shape.HORIZONTAL_STEP, CraftingPattern.ROTATE, verticalStep, step);
            }
            if (material.block("stairs") == null) {
                if (CraftingPattern.wildcardNativeCuts(material.id)) {
                    // Six halves make four three-quarter stairs without changing vanilla's full-block wildcard stair
                    // recipes.
                    add(material, Shape.STAIRS, CraftingPattern.STAIRS, horizontal, false);
                } else {
                    add(material, Shape.STAIRS, CraftingPattern.STAIRS, full, half);
                }
            }
            if (material.block("wall") == null) {
                add(material, Shape.WALL, CraftingPattern.WALL, full, half);
            }
            if (material.block("pane") == null) {
                add(material, Shape.PANE, CraftingPattern.PANE, full, half);
            }

            if (horizontal != null && vertical != null) {
                register(material.id, Shape.SLAB, CraftingPattern.ROTATE, vertical, horizontal);
            }
        }

        SlabRecombinationRecipe.register(registry);
        LegacyPieceRecipe.register(registry);
        currentRegistry = null;
    }

    private static void add(Catalogue.MaterialDef material, Shape shape, CraftingPattern pattern, ItemStack ingredient,
            boolean half) {
        ItemStack output = Pieces.stack(Pieces.state(material.id, shape, shape == Shape.SLAB ? 1 : 0),
                pattern.yield(half));

        if (output == null) {
            return;
        }
        // Leave the other mod's normal cuts alone. Two diagonal full blocks make four slabs; six of our half slabs make
        // four stairs.
        if (nativeConflict(pattern, ingredient, output)) {
            if (shape == Shape.SLAB && pattern == CraftingPattern.SLAB && !half) {
                pattern = CraftingPattern.STEP;
                output.setCount(pattern.yield(false));
            } else if (shape == Shape.STAIRS && pattern == CraftingPattern.STAIRS && !half) {
                ingredient = Pieces.stack(Pieces.state(material.id, Shape.SLAB, 1), 1);
            } else {
                throw new IllegalStateException("Conflicting native recipe for " + material.id + " / " + shape);
            }
            if (ingredient == null || nativeConflict(pattern, ingredient, output)) {
                throw new IllegalStateException("No safe cutting recipe for " + material.id + " / " + shape);
            }
        }

        register(material.id, shape, pattern, ingredient, output);
    }

    private static boolean nativeConflict(CraftingPattern pattern, ItemStack ingredient, ItemStack output) {
        net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(
                new net.minecraft.inventory.Container() {
                    @Override
                    public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                        return true;
                    }
                }, 3, 3);

        for (int blockY = 0; blockY < pattern.height(); blockY++) {
            for (int blockX = 0; blockX < pattern.width(); blockX++) {
                if (pattern.occupied(blockX, blockY)) {
                    grid.setInventorySlotContents(blockX + blockY * 3, ingredient.copy());
                }
            }
        }
        for (IRecipe recipe : currentRegistry.getValuesCollection()) {
            if (recipe instanceof CuttingRecipe) {
                continue;
            }
            // These ordinary recipe implementations do not consult the world.
            if (!(recipe instanceof ShapedRecipes || recipe instanceof net.minecraft.item.crafting.ShapelessRecipes
                    || recipe instanceof net.minecraftforge.oredict.ShapedOreRecipe
                    || recipe instanceof net.minecraftforge.oredict.ShapelessOreRecipe)) {
                continue;
            }
            if (recipe.matches(grid, null) && !ItemStack.areItemStacksEqual(output, recipe.getCraftingResult(grid))) {
                return true;
            }
        }

        return false;
    }

    private static void register(String material, Shape shape, CraftingPattern pattern, ItemStack ingredient,
            ItemStack output) {
        CuttingRecipe recipe = new CuttingRecipe(material, shape, pattern, ingredient, output);

        REGISTERED.add(recipe);
        recipe.setRegistryName("skysbuildingpieces",
                material.replace(':', '/') + "/" + shape.name().toLowerCase(java.util.Locale.ROOT) + "/"
                        + pattern.name().toLowerCase(java.util.Locale.ROOT) + "/"
                        + ingredient.getItem().getRegistryName().toString().replace(':', '/') + "_"
                        + ingredient.getMetadata());
        currentRegistry.register(recipe);
    }

    private static IForgeRegistry<IRecipe> currentRegistry;
}
