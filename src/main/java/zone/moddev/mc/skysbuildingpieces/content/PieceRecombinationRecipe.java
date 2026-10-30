package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.world.World;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.oredict.RecipeSorter;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** A vertical pair rebuilds a larger piece without claiming the existing cutting recipes. */
public final class PieceRecombinationRecipe extends ShapedRecipes {
    private final PieceBlock inputBlock;
    private final ItemStack ingredient;

    private PieceRecombinationRecipe(IBlockState input, ItemStack output) {
        super("skysbuildingpieces/recombine/" + ((PieceBlock) input.getBlock()).palette.shape.name(), 1, 2, NonNullList.from(Ingredient.EMPTY,
                PieceIngredients.fromState(input), PieceIngredients.fromState(input)),
                output.copy());
        inputBlock = (PieceBlock) input.getBlock();
        ingredient = Pieces.stack(input, 1);
    }

    public static void register(IForgeRegistry<IRecipe> registry) {
        RecipeSorter.register("skysbuildingpieces:piece_recombination", PieceRecombinationRecipe.class,
                RecipeSorter.Category.SHAPED, "after:minecraft:shaped");

        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            ItemStack step = Pieces.stack(Pieces.state(material.id, Shape.HORIZONTAL_STEP, 0), 1);
            add(registry, material.id, Shape.CORNER, step);

            ItemStack slab = slab(material.id);
            add(registry, material.id, Shape.HORIZONTAL_STEP, slab);
            add(registry, material.id, Shape.VERTICAL_STEP, slab);
        }
    }

    private static ItemStack slab(String material) {
        ItemStack regular = Pieces.stack(Pieces.state(material, Shape.SLAB, 1), 1);
        if (regular != null) {
            return regular;
        }

        // Soil slabs belong to Grass Slabs, not this catalogue. Use them when installed,
        // otherwise ordinary dirt can still return its existing vertical slab.
        String grassSlab = material.equals("minecraft:grass") ? "grass_slab"
                : material.equals("minecraft:dirt") ? "dirt_slab" : null;
        Item external = grassSlab == null ? null : Item.getByNameOrId("skysgrassslabs:" + grassSlab);
        if (external != null) {
            return new ItemStack(external);
        }
        return Pieces.stack(Pieces.state(material, Shape.VERTICAL_SLAB, 0), 1);
    }

    private static void add(IForgeRegistry<IRecipe> registry, String material, Shape shape, ItemStack output) {
        IBlockState input = Pieces.state(material, shape, 0);
        if (input != null && output != null) {
            registry.register(new PieceRecombinationRecipe(input, output).setRegistryName("skysbuildingpieces",
                    material.replace(':', '/') + "/recombine_" + shape.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        int firstSlot = -1;
        int secondSlot = -1;
        for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
            ItemStack stack = grid.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!sameMaterial(stack) || secondSlot != -1) {
                return false;
            }
            if (firstSlot == -1) {
                firstSlot = slot;
            } else {
                secondSlot = slot;
            }
        }

        return firstSlot >= 0 && secondSlot == firstSlot + grid.getWidth();
    }

    private boolean sameMaterial(ItemStack stack) {
        int metadata = stack.getItemDamage();
        return !stack.isEmpty() && stack.getItem() == ingredient.getItem() && metadata >= 0 && metadata < 16
                && inputBlock.canonicalMeta(inputBlock.getStateFromMeta(metadata)) == ingredient.getItemDamage();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting grid) {
        return NonNullList.withSize(grid.getSizeInventory(), ItemStack.EMPTY);
    }
}
