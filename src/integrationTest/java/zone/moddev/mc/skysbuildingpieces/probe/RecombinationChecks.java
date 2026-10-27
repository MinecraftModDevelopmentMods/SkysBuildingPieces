package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Checks rebuilding larger pieces against the complete installed recipe set. */
public final class RecombinationChecks {
    public static void run(WorldServer world) {
        if (net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks")) {
            return;
        }

        int checks = 0;
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            ItemStack step = Pieces.stack(Pieces.state(material.id, Shape.HORIZONTAL_STEP, 0), 1);
            ItemStack slab = slab(material.id);
            checks += pair(world, material.id, Shape.CORNER, step);
            checks += pair(world, material.id, Shape.HORIZONTAL_STEP, slab);
            checks += pair(world, material.id, Shape.VERTICAL_STEP, slab);
        }
        require(checks > 0, "the catalogue supplies recombination recipes");
        System.out.println("BUILDING_PIECES_SMALL_RECOMBINATION_PASS cases=" + checks
                + " modules=" + Catalogue.INSTANCE.modules.size());
    }

    private static ItemStack slab(String material) {
        ItemStack regular = Pieces.stack(Pieces.state(material, Shape.SLAB, 1), 1);
        if (regular != null) {
            return regular;
        }

        String grassSlab = material.equals("minecraft:grass") ? "grass_slab"
                : material.equals("minecraft:dirt") ? "dirt_slab" : null;
        Item external = grassSlab == null ? null : Item.getByNameOrId("skysgrassslabs:" + grassSlab);
        if (external != null) {
            return new ItemStack(external);
        }
        return Pieces.stack(Pieces.state(material, Shape.VERTICAL_SLAB, 0), 1);
    }

    private static int pair(WorldServer world, String material, Shape shape, ItemStack expected) {
        IBlockState source = Pieces.state(material, shape, 0);
        if (source == null || expected == null) {
            return 0;
        }

        PieceBlock block = (PieceBlock) source.getBlock();
        int cases = 0;
        for (int size : new int[] { 2, 3 }) {
            for (int column = 0; column < size; column++) {
                for (int row = 0; row < size - 1; row++) {
                    for (int orientation = 0; orientation < shape.states; orientation++) {
                        InventoryCrafting grid = grid(size);
                        int upperSlot = column + row * size;
                        ItemStack upper = new ItemStack(block, 17,
                                block.getMetaFromState(Pieces.state(material, shape, orientation)));
                        ItemStack lower = new ItemStack(block, 23,
                                block.getMetaFromState(Pieces.state(material, shape, (orientation + 1) % shape.states)));
                        grid.setInventorySlotContents(upperSlot, upper);
                        grid.setInventorySlotContents(upperSlot + size, lower);

                        String label = material + " " + shape + " orientation=" + orientation;
                        ItemStack actual = CraftingManager.getInstance().findMatchingRecipe(grid, world);
                        require(ItemStack.areItemStacksEqual(expected, actual), "two vertically stacked pieces: " + label);
                        for (IRecipe recipe : CraftingManager.getInstance().getRecipeList()) {
                            if (recipe.matches(grid, world)) {
                                require(ItemStack.areItemStacksEqual(expected, recipe.getCraftingResult(grid)),
                                        "no conflicting recipe for " + label);
                                for (ItemStack remainder : recipe.getRemainingItems(grid)) {
                                    require(remainder == null, "both piece units are consumed");
                                }
                            }
                        }
                        actual.stackSize = 9;
                        require(ItemStack.areItemStacksEqual(expected,
                                CraftingManager.getInstance().findMatchingRecipe(grid, world)),
                                "each crafting result is an independent one-item copy");
                        require(upper.stackSize == 17 && lower.stackSize == 23, "recipe lookup leaves inputs untouched");
                        cases++;
                    }
                }
            }
        }

        // A different slot in the same metadata palette must never become this material.
        InventoryCrafting mixed = grid(2);
        ItemStack ingredient = Pieces.stack(source, 1);
        mixed.setInventorySlotContents(0, ingredient);
        int otherMaterial = block.canonicalMeta(source) == 0 ? shape.states : 0;
        if (otherMaterial / shape.states < block.palette.materials.size()) {
            mixed.setInventorySlotContents(2, new ItemStack(block, 1, otherMaterial));
            require(CraftingManager.getInstance().findMatchingRecipe(mixed, world) == null,
                    "different materials in one palette do not recombine");
        }
        mixed.setInventorySlotContents(2, new ItemStack(block, 1, 16));
        require(CraftingManager.getInstance().findMatchingRecipe(mixed, world) == null, "out-of-range metadata is rejected");
        mixed.setInventorySlotContents(2, ingredient.copy());
        mixed.setInventorySlotContents(1, ingredient.copy());
        require(CraftingManager.getInstance().findMatchingRecipe(mixed, world) == null, "an extra piece is rejected");
        mixed.setInventorySlotContents(1, null);
        mixed.setInventorySlotContents(2, null);
        // A single step may still use the existing one-for-one rotation recipe.
        require(!ItemStack.areItemStacksEqual(expected, CraftingManager.getInstance().findMatchingRecipe(mixed, world)),
                "one piece cannot produce the larger shape");

        if (shape == Shape.CORNER) {
            mixed.setInventorySlotContents(1, ingredient.copy());
            require(CraftingManager.getInstance().findMatchingRecipe(mixed, world) == null,
                    "corners placed horizontally do not claim the cutting pattern");
        }
        return cases;
    }

    private static InventoryCrafting grid(int size) {
        return new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, size, size);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private RecombinationChecks() {
    }
}
