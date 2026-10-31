package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.CuttingRecipe;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Check real crafting results, every matching recipe and client decoding. */
public final class RecipeChecks {
    public static int run(ServerWorld world) {
        int recipes = 0;
        int variants = 0;
        Set<Item> obtainable = new HashSet<>();

        for (IRecipe<?> entry : world.getRecipeManager().getRecipes()) {
            if (!entry.getId().getNamespace().startsWith("skysbuildingpieces") || entry.isDynamic()) {
                continue;
            }

            require(entry instanceof ShapedRecipe, "Expected an ordinary shaped recipe: " + entry.getId());

            ShapedRecipe recipe = (ShapedRecipe) entry;

            obtainable.add(recipe.getRecipeOutput().getItem());

            for (int size : new int[] { 2, 3 }) {
                if (!recipe.canFit(size, size)) {
                    continue;
                }
                for (int rowOffset = 0; rowOffset <= size - recipe.getRecipeHeight(); rowOffset++) {
                    for (int columnOffset = 0; columnOffset <= size - recipe.getRecipeWidth(); columnOffset++) {
                        for (boolean mirror : new boolean[] { false, true }) {
                            CraftingInventory grid = grid(size);

                            for (int row = 0; row < recipe.getRecipeHeight(); row++) {
                                for (int column = 0; column < recipe.getRecipeWidth(); column++) {
                                    int ingredientColumn = mirror ? recipe.getRecipeWidth() - 1 - column : column;
                                    Ingredient ingredient = recipe.getIngredients()
                                            .get(ingredientColumn + row * recipe.getRecipeWidth());

                                    if (ingredient == Ingredient.EMPTY) {
                                        continue;
                                    }

                                    require(ingredient.getMatchingStacks().length > 0,
                                            "Empty book ingredient: " + recipe.getId());

                                    ItemStack input = ingredient.getMatchingStacks()[0].copy();

                                    input.setCount(17);
                                    grid.setInventorySlotContents(column + columnOffset + (row + rowOffset) * size,
                                            input);
                                }
                            }

                            require(recipe.matches(grid, world), "Offset or mirrored recipe: " + recipe.getId());

                            ItemStack expected = recipe.getRecipeOutput().copy();

                            assertManager(grid, world, expected, recipe.getId().toString());

                            for (ItemStack remainder : recipe.getRemainingItems(grid)) {
                                require(remainder.isEmpty(), "Cutting consumes ingredients without a tool remainder");
                            }

                            int first = -1;
                            int empty = -1;

                            for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
                                if (grid.getStackInSlot(slot).isEmpty()) {
                                    empty = slot;
                                } else if (first < 0) {
                                    first = slot;
                                }
                            }

                            ItemStack input = grid.getStackInSlot(first);

                            require(input.getCount() == 17, "Recipe lookup changed an input stack");
                            grid.setInventorySlotContents(first, new ItemStack(Items.APPLE));
                            require(!recipe.matches(grid, world), "Wrong material accepted: " + recipe.getId());
                            grid.setInventorySlotContents(first, input);

                            if (empty >= 0) {
                                grid.setInventorySlotContents(empty, input.copy());
                                require(!recipe.matches(grid, world), "Extra material accepted: " + recipe.getId());
                                grid.setInventorySlotContents(empty, ItemStack.EMPTY);
                            }
                            if (recipe instanceof CuttingRecipe) {
                                CompoundNBT tag = new CompoundNBT();

                                tag.putString("custom_label", "Saved vertical piece");
                                tag.putLong("custom_number", 1234567890123L);
                                input.setTag(tag);

                                ItemStack named = expected.copy();

                                named.setTag(tag.copy());
                                assertManager(grid, world, named, "Rotation keeps the complete custom tag");

                                ItemStack result = recipe.getCraftingResult(grid);

                                result.getTag().putString("custom_label", "Changed result");
                                require(input.getTag().equals(tag), "Rotation result must not share the input tag");
                            }

                            variants++;
                        }
                    }
                }
            }

            network(recipe);
            recipes++;
        }

        coverage(obtainable);
        nativeRecipe(world, "stone_slab", Items.STONE_SLAB, 6);
        nativeRecipe(world, "smooth_stone_slab", Items.SMOOTH_STONE_SLAB, 6);
        nativeRecipe(world, "stone_button", Items.STONE_BUTTON, 1);
        nativeRecipe(world, "stone_pressure_plate", Items.STONE_PRESSURE_PLATE, 1);
        nativeRecipe(world, "oak_slab", Items.OAK_SLAB, 6);
        nativeRecipe(world, "glass_pane", Items.GLASS_PANE, 16);
        nativeRecipe(world, "nether_brick_fence", Items.NETHER_BRICK_FENCE, 6);
        templates(world);
        require(recipes > 1000, "The native catalogue is incomplete");
        System.out.println("BUILDING_PIECES_CRAFTING_PASS recipes=" + recipes + " variants=" + variants
                + " conflicts=0 templates=retired network=true");
        return recipes;
    }

    private static void coverage(Set<Item> obtainable) {
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            if (material.type.equals("path")) {
                continue; // Paths are made with a shovel; rebuilding does not create extra material.
            }
            for (Shape shape : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB, Shape.HORIZONTAL_STEP,
                    Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS, Shape.WALL, Shape.PANE }) {
                BlockState state = Pieces.state(material.id, shape, shape == Shape.SLAB ? 1 : 0);

                if (state == null || !(state.getBlock() instanceof PieceBlock)
                        || shape == Shape.VERTICAL_SLAB && Pieces.state(material.id, Shape.SLAB, 1) != null) {
                    continue;
                }

                require(obtainable.contains(Pieces.stack(state, 1).getItem()),
                        "No recipe obtains " + material.id + " / " + shape);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void network(ShapedRecipe recipe) {
        IRecipeSerializer serializer = recipe.getSerializer();
        PacketBuffer buffer = new PacketBuffer(io.netty.buffer.Unpooled.buffer());

        try {
            serializer.write(buffer, recipe);

            IRecipe decoded = serializer.read(recipe.getId(), buffer);

            require(decoded.getId().equals(recipe.getId()) && !decoded.isDynamic(),
                    "Network recipe identity and visibility");
            require(ItemStack.areItemStacksEqual(decoded.getRecipeOutput(), recipe.getRecipeOutput()),
                    "Network result changed: " + recipe.getId());

            CraftingInventory grid = grid(3);

            for (int row = 0; row < recipe.getRecipeHeight(); row++) {
                for (int column = 0; column < recipe.getRecipeWidth(); column++) {
                    Ingredient ingredient = recipe.getIngredients().get(column + row * recipe.getRecipeWidth());

                    if (ingredient != Ingredient.EMPTY) {
                        grid.setInventorySlotContents(column + row * 3, ingredient.getMatchingStacks()[0].copy());
                    }
                }
            }

            require(ItemStack.areItemStacksEqual(decoded.getCraftingResult(grid), recipe.getCraftingResult(grid)),
                    "Network assembly changed: " + recipe.getId());
        } finally {
            buffer.release();
        }
    }

    private static void nativeRecipe(ServerWorld world, String path, Item output, int count) {
        IRecipe<?> recipe = world.getRecipeManager().getRecipe(new ResourceLocation("minecraft", path))
                .orElseThrow(() -> new AssertionError("Missing native recipe: " + path));
        CraftingInventory grid = grid(3);
        int width = recipe instanceof ShapedRecipe ? ((ShapedRecipe) recipe).getRecipeWidth() : 3;

        for (int index = 0; index < recipe.getIngredients().size(); index++) {
            Ingredient ingredient = recipe.getIngredients().get(index);

            if (ingredient != Ingredient.EMPTY) {
                grid.setInventorySlotContents(index % width + index / width * 3,
                        ingredient.getMatchingStacks()[0].copy());
            }
        }

        assertManager(grid, world, new ItemStack(output, count), "native " + path);
    }

    private static void templates(ServerWorld world) {
        for (Item retired : Pieces.RETIRED_TEMPLATES.values()) {
            for (IRecipe<?> recipe : world.getRecipeManager().getRecipes()) {
                require(recipe.getRecipeOutput().getItem() != retired, "Retired templates must not be craftable");
            }

            CraftingInventory grid = grid(2);
            CompoundNBT custom = new CompoundNBT();

            custom.putString("custom_label", "Old template");
            custom.putLong("custom_number", 1234567890123L);

            ItemStack input = new ItemStack(retired);

            input.setTag(custom);
            grid.setInventorySlotContents(3, input);

            ItemStack paper = new ItemStack(Items.PAPER);

            paper.setTag(custom.copy());
            assertManager(grid, world, paper, "retired template recycling");

            ICraftingRecipe recycling = world.getRecipeManager().getRecipe(IRecipeType.CRAFTING, grid, world).get();

            for (ItemStack remainder : recycling.getRemainingItems(grid)) {
                require(remainder.isEmpty(), "Recycling consumes the retired template");
            }

            grid.setInventorySlotContents(0, new ItemStack(Items.STONE));
            require(!recycling.matches(grid, world), "Old templates must not cut new pieces");
        }
    }

    private static void assertManager(CraftingInventory grid, ServerWorld world, ItemStack expected, String label) {
        ItemStack actual = world.getRecipeManager().getRecipe(IRecipeType.CRAFTING, grid, world)
                .map(recipe -> recipe.getCraftingResult(grid)).orElse(ItemStack.EMPTY);

        require(ItemStack.areItemStacksEqual(expected, actual), "Crafting manager selects " + label);

        for (ICraftingRecipe recipe : world.getRecipeManager().getRecipes(IRecipeType.CRAFTING, grid, world)) {
            require(ItemStack.areItemStacksEqual(expected, recipe.getCraftingResult(grid)),
                    "Conflicting recipe for " + label + ": " + recipe.getId());
        }
    }

    static CraftingInventory grid(int size) {
        return RecombinationChecks.grid(size);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private RecipeChecks() {
    }
}
