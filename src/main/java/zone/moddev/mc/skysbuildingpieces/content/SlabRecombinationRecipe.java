package zone.moddev.mc.skysbuildingpieces.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraft.util.NonNullList;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.oredict.RecipeSorter;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** A horizontal pair leaves vanilla's vertical chiseled-block recipes intact. */
public final class SlabRecombinationRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    private static final List<SlabRecombinationRecipe> RECIPES = new ArrayList<>();
    public final ItemStack slab, full;
    private final Block block;

    public SlabRecombinationRecipe(IBlockState slab, ItemStack full) {
        this.block = slab.getBlock();
        this.slab = Pieces.stack(slab, 1);
        this.full = full.copy();
    }

    public static List<SlabRecombinationRecipe> recipes() {
        return Collections.unmodifiableList(RECIPES);
    }

    public static void register(IForgeRegistry<IRecipe> registry) {
        RecipeSorter.register("skysbuildingpieces:slab_recombination", SlabRecombinationRecipe.class,
                RecipeSorter.Category.SHAPED, "after:minecraft:shaped");

        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            IBlockState state = Pieces.state(material.id, Shape.SLAB, 1);
            ItemStack full = Pieces.stack(Pieces.nativeState(material, "full"), 1);

            // Smooth stone has no obtainable full-block item in this target.
            if (state == null || full == null || full.getItem() == null) {
                continue;
            }

            SlabRecombinationRecipe recipe = new SlabRecombinationRecipe(state, full);

            recipe.setRegistryName("skysbuildingpieces", material.id.replace(':', '/') + "/slab_recombination");
            RECIPES.add(recipe);
            registry.register(recipe);
        }
    }

    private boolean same(ItemStack input) {
        if (input == null || input.isEmpty() || input.getItem() != slab.getItem()) {
            return false;
        }

        int damage = input.getItemDamage();

        if (damage < 0 || damage > 15) {
            return false;
        }
        if (block instanceof PieceBlock) {
            PieceBlock piece = (PieceBlock) block;

            try {
                return piece.canonicalMeta(piece.getStateFromMeta(damage)) == slab.getItemDamage();
            } catch (IllegalArgumentException unusedSlot) {
                return false;
            }
        }

        return block instanceof BlockSlab ? (damage & 7) == slab.getItemDamage() : damage == slab.getItemDamage();
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        int first = -1;
        int second = -1;

        for (int slotIndex = 0; slotIndex < grid.getSizeInventory(); slotIndex++) {
            if (!grid.getStackInSlot(slotIndex).isEmpty()) {
                if (!same(grid.getStackInSlot(slotIndex)) || second != -1) {
                    return false;
                }
                if (first == -1) {
                    first = slotIndex;
                } else {
                    second = slotIndex;
                }
            }
        }

        return second == first + 1 && first >= 0 && first / grid.getWidth() == second / grid.getWidth();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        return full.copy();
    }

    @Override
    public ItemStack getRecipeOutput() {
        return full.copy();
    }

    @Override
    public boolean canFit(int width, int height) {
        return width >= 2 && height >= 1;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.from(Ingredient.EMPTY, Ingredient.fromStacks(slab), Ingredient.fromStacks(slab));
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting grid) {
        return NonNullList.withSize(grid.getSizeInventory(), ItemStack.EMPTY);
    }
}
