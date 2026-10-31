package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.BlockState;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Checks rebuilding larger pieces against the complete installed recipe set. */
public final class RecombinationChecks {
    public static void run(ServerWorld world) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("buildingbricks")) {
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
        System.out.println("BUILDING_PIECES_SMALL_RECOMBINATION_PASS cases=" + checks + " modules="
                + Catalogue.INSTANCE.modules.size());
    }

    private static ItemStack slab(String material) {
        ItemStack regular = Pieces.stack(Pieces.state(material, Shape.SLAB, 1), 1);

        if (regular != null) {
            return regular;
        }

        String grassSlab = material.equals("minecraft:grass") ? "grass_slab"
                : material.equals("minecraft:dirt") ? "dirt_slab" : null;
        Item external = grassSlab == null ? null
                : net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.util.ResourceLocation("skysgrassslabs:" + grassSlab));

        if (external != null && external != net.minecraft.item.Items.AIR) {
            return new ItemStack(external);
        }

        return Pieces.stack(Pieces.state(material, Shape.VERTICAL_SLAB, 0), 1);
    }

    private static int pair(ServerWorld world, String material, Shape shape, ItemStack expected) {
        BlockState source = Pieces.state(material, shape, 0);

        if (source == null || expected == null) {
            return 0;
        }

        PieceBlock block = (PieceBlock) source.getBlock();
        int cases = 0;

        for (int size : new int[] { 2, 3 }) {
            for (int column = 0; column < size; column++) {
                for (int row = 0; row < size - 1; row++) {
                    for (int orientation = 0; orientation < shape.states; orientation++) {
                        CraftingInventory grid = grid(size);
                        int upperSlot = column + row * size;
                        ItemStack upper = Pieces.stack(Pieces.state(material, shape, orientation), 17);
                        ItemStack lower = Pieces.stack(Pieces.state(material, shape, (orientation + 1) % shape.states),
                                23);

                        grid.setInventorySlotContents(upperSlot, upper);
                        grid.setInventorySlotContents(upperSlot + size, lower);

                        String label = material + " " + shape + " orientation=" + orientation;
                        ItemStack actual = result(grid, world);

                        require(ItemStack.areItemStacksEqual(expected, actual),
                                "two vertically stacked pieces: " + label);

                        for (net.minecraft.item.crafting.ICraftingRecipe recipe : world.getRecipeManager()
                                .getRecipes(IRecipeType.CRAFTING, grid, world)) {
                            if (recipe.matches(grid, world)) {
                                require(ItemStack.areItemStacksEqual(expected, recipe.getCraftingResult(grid)),
                                        "no conflicting recipe for " + label);

                                for (ItemStack remainder : recipe.getRemainingItems(grid)) {
                                    require(remainder.isEmpty(), "both piece units are consumed");
                                }
                            }
                        }

                        actual.setCount(9);
                        require(ItemStack.areItemStacksEqual(expected, result(grid, world)),
                                "each crafting result is an independent one-item copy");
                        require(upper.getCount() == 17 && lower.getCount() == 23,
                                "recipe lookup leaves inputs untouched");
                        cases++;
                    }
                }
            }
        }

        // A different slot in the same metadata palette must never become this material.
        CraftingInventory mixed = grid(2);
        ItemStack ingredient = Pieces.stack(source, 1);

        mixed.setInventorySlotContents(0, ingredient);

        int otherMaterial = block.canonicalMeta(source) == 0 ? shape.states : 0;

        if (otherMaterial / shape.states < block.palette.materials.size()) {
            mixed.setInventorySlotContents(2, Pieces.stack(block.getStateFromMeta(otherMaterial), 1));
            require(result(mixed, world).isEmpty(), "different materials in one palette do not recombine");
        }

        ItemStack invalidCarrier = new ItemStack(block);
        net.minecraft.nbt.CompoundNBT invalid = new net.minecraft.nbt.CompoundNBT();

        invalid.putInt("Damage", 16);
        invalidCarrier.setTag(invalid);
        mixed.setInventorySlotContents(2, invalidCarrier);
        require(result(mixed, world).isEmpty(), "unconverted invalid palette carriers are rejected");
        mixed.setInventorySlotContents(2, ingredient.copy());
        mixed.setInventorySlotContents(1, ingredient.copy());
        require(result(mixed, world).isEmpty(), "an extra piece is rejected");
        mixed.setInventorySlotContents(1, ItemStack.EMPTY);
        mixed.setInventorySlotContents(2, ItemStack.EMPTY);
        // A single step may still use the existing one-for-one rotation recipe.
        require(!ItemStack.areItemStacksEqual(expected, result(mixed, world)),
                "one piece cannot produce the larger shape");

        if (shape == Shape.CORNER) {
            mixed.setInventorySlotContents(1, ingredient.copy());
            require(result(mixed, world).isEmpty(), "corners placed horizontally do not claim the cutting pattern");
        }

        return cases;
    }

    static CraftingInventory grid(int size) {
        return new CraftingInventory(new Container(null, 0) {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.PlayerEntity player) {
                return true;
            }
        }, size, size);
    }

    private static ItemStack result(CraftingInventory grid, ServerWorld world) {
        return world.getRecipeManager().getRecipe(IRecipeType.CRAFTING, grid, world)
                .map(recipe -> recipe.getCraftingResult(grid)).orElse(ItemStack.EMPTY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private RecombinationChecks() {
    }
}
