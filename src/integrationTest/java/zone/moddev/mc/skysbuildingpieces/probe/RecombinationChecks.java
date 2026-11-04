package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Checks rebuilding larger pieces against the complete installed recipe set. */
public final class RecombinationChecks {
    public static void run(ServerLevel world) {
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
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_SMALL_RECOMBINATION_PASS cases=" + checks + " modules="
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
                        .getValue(new net.minecraft.resources.ResourceLocation("skysgrassslabs:" + grassSlab));

        if (external != null && external != net.minecraft.world.item.Items.AIR) {
            return new ItemStack(external);
        }

        return Pieces.stack(Pieces.state(material, Shape.VERTICAL_SLAB, 0), 1);
    }

    private static int pair(ServerLevel world, String material, Shape shape, ItemStack expected) {
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
                        CraftingContainer grid = grid(size);
                        int upperSlot = column + row * size;
                        ItemStack upper = Pieces.stack(Pieces.state(material, shape, orientation), 17);
                        ItemStack lower = Pieces.stack(Pieces.state(material, shape, (orientation + 1) % shape.states),
                                23);

                        grid.setItem(upperSlot, upper);
                        grid.setItem(upperSlot + size, lower);

                        String label = material + " " + shape + " orientation=" + orientation;
                        ItemStack actual = result(grid, world);

                        require(ItemStack.matches(expected, actual),
                                "two vertically stacked pieces: " + label);

                        for (net.minecraft.world.item.crafting.CraftingRecipe recipe : world.getRecipeManager()
                                .getRecipesFor(RecipeType.CRAFTING, grid, world)) {
                            if (recipe.matches(grid, world)) {
                                require(ItemStack.matches(expected, recipe.assemble(grid, net.minecraft.core.RegistryAccess.EMPTY)),
                                        "no conflicting recipe for " + label);

                                for (ItemStack remainder : recipe.getRemainingItems(grid)) {
                                    require(remainder.isEmpty(), "both piece units are consumed");
                                }
                            }
                        }

                        actual.setCount(9);
                        require(ItemStack.matches(expected, result(grid, world)),
                                "each crafting result is an independent one-item copy");
                        require(upper.getCount() == 17 && lower.getCount() == 23,
                                "recipe lookup leaves inputs untouched");
                        cases++;
                    }
                }
            }
        }

        // A different slot in the same metadata palette must never become this material.
        CraftingContainer mixed = grid(2);
        ItemStack ingredient = Pieces.stack(source, 1);

        mixed.setItem(0, ingredient);

        int otherMaterial = block.canonicalMeta(source) == 0 ? shape.states : 0;

        if (otherMaterial / shape.states < block.palette.materials.size()) {
            mixed.setItem(2, Pieces.stack(block.getStateFromMeta(otherMaterial), 1));
            require(result(mixed, world).isEmpty(), "different materials in one palette do not recombine");
        }

        ItemStack invalidCarrier = new ItemStack(block);
        net.minecraft.nbt.CompoundTag invalid = new net.minecraft.nbt.CompoundTag();

        invalid.putInt("Damage", 16);
        invalidCarrier.setTag(invalid);
        mixed.setItem(2, invalidCarrier);
        require(result(mixed, world).isEmpty(), "unconverted invalid palette carriers are rejected");
        mixed.setItem(2, ingredient.copy());
        mixed.setItem(1, ingredient.copy());
        require(result(mixed, world).isEmpty(), "an extra piece is rejected");
        mixed.setItem(1, ItemStack.EMPTY);
        mixed.setItem(2, ItemStack.EMPTY);
        // A single step may still use the existing one-for-one rotation recipe.
        require(!ItemStack.matches(expected, result(mixed, world)),
                "one piece cannot produce the larger shape");

        if (shape == Shape.CORNER) {
            mixed.setItem(1, ingredient.copy());
            require(result(mixed, world).isEmpty(), "corners placed horizontally do not claim the cutting pattern");
        }

        return cases;
    }

    static CraftingContainer grid(int size) {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override
            public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        }, size, size);
    }

    private static ItemStack result(CraftingContainer grid, ServerLevel world) {
        return world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, world)
                .map(recipe -> recipe.assemble(grid, net.minecraft.core.RegistryAccess.EMPTY)).orElse(ItemStack.EMPTY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private RecombinationChecks() {
    }
}
