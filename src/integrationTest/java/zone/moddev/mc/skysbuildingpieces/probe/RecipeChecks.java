package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.CuttingRecipe;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Check real crafting results, every matching recipe and client decoding. */
public final class RecipeChecks {
    public static int run(ServerLevel world) {
        Audit audit = new Audit(world);
        while (!audit.advance(24)) {
            // Older harnesses call this from one dedicated verification task.
        }
        return audit.finish();
    }

    /** Lets a real server keep ticking while we exhaust the recipe catalogue. */
    static final class Audit {
        private final ServerLevel world;
        private final java.util.Iterator<RecipeHolder<?>> remaining;
        private final Set<Item> obtainable = new HashSet<>();
        private int recipes;
        private int variants;

        Audit(ServerLevel world) {
            this.world = world;
            remaining = world.getRecipeManager().getRecipes().iterator();
        }

        boolean advance(int limit) {
            for (int checked = 0; checked < limit && remaining.hasNext(); checked++) {
                RecipeHolder<?> holder = remaining.next();
                ResourceLocation id = holder.id();
                Recipe<?> entry = holder.value();
                if (!id.getNamespace().startsWith("skysbuildingpieces") || entry.isSpecial()) {
                    continue;
                }

                if (entry instanceof net.minecraft.world.item.crafting.ShapelessRecipe) {
                    net.minecraft.world.item.crafting.ShapelessRecipe recipe = (net.minecraft.world.item.crafting.ShapelessRecipe) entry;

                    require(id.getNamespace().equals("skysbuildingpiecesbop")
                            && id.getPath().startsWith("restore_") && recipe.getIngredients().size() == 1,
                            "Unexpected shapeless building recipe: " + id);

                    for (int size : new int[] { 2, 3 }) {
                        for (int slot = 0; slot < size * size; slot++) {
                            CraftingContainer grid = grid(size);
                            ItemStack input = recipe.getIngredients().get(0).getItems()[0].copy();

                            input.setCount(17);
                            grid.setItem(slot, input);
                            assertManager(grid, world, recipe.getResultItem(world.registryAccess()), id.toString());
                            require(input.getCount() == 17, "Restore lookup changed its input");
                            grid.setItem((slot + 1) % (size * size), input.copy());
                            require(!recipe.matches(grid.asCraftInput(), world), "Restore recipe accepts an extra block");
                            variants++;
                        }
                    }

                    obtainable.add(recipe.getResultItem(world.registryAccess()).getItem());
                    network(recipe, id, world);
                    recipes++;
                    continue;
                }

                require(entry instanceof ShapedRecipe, "Expected an ordinary shaped recipe: " + id);

                ShapedRecipe recipe = (ShapedRecipe) entry;

                obtainable.add(recipe.getResultItem(world.registryAccess()).getItem());

                for (int size : new int[] { 2, 3 }) {
                    if (!recipe.canCraftInDimensions(size, size)) {
                        continue;
                    }
                    for (int rowOffset = 0; rowOffset <= size - recipe.getRecipeHeight(); rowOffset++) {
                        for (int columnOffset = 0; columnOffset <= size - recipe.getRecipeWidth(); columnOffset++) {
                            for (boolean mirror : new boolean[] { false, true }) {
                                CraftingContainer grid = grid(size);

                                for (int row = 0; row < recipe.getRecipeHeight(); row++) {
                                    for (int column = 0; column < recipe.getRecipeWidth(); column++) {
                                        int ingredientColumn = mirror ? recipe.getRecipeWidth() - 1 - column : column;
                                        Ingredient ingredient = recipe.getIngredients()
                                                .get(ingredientColumn + row * recipe.getRecipeWidth());

                                        if (ingredient == Ingredient.EMPTY) {
                                            continue;
                                        }

                                        require(ingredient.getItems().length > 0,
                                                "Empty book ingredient: " + id);

                                        ItemStack input = ingredient.getItems()[0].copy();

                                        input.setCount(17);
                                        grid.setItem(column + columnOffset + (row + rowOffset) * size,
                                                input);
                                    }
                                }

                                require(recipe.matches(grid.asCraftInput(), world), "Offset or mirrored recipe: " + id);

                                ItemStack expected = recipe.getResultItem(world.registryAccess()).copy();

                                assertManager(grid, world, expected, id.toString());

                                for (ItemStack remainder : recipe.getRemainingItems(grid.asCraftInput())) {
                                    require(remainder.isEmpty(), "Cutting consumes ingredients without a tool remainder");
                                }

                                int first = -1;
                                int empty = -1;

                                for (int slot = 0; slot < grid.getContainerSize(); slot++) {
                                    if (grid.getItem(slot).isEmpty()) {
                                        empty = slot;
                                    } else if (first < 0) {
                                        first = slot;
                                    }
                                }

                                ItemStack input = grid.getItem(first);

                                require(input.getCount() == 17, "Recipe lookup changed an input stack");
                                grid.setItem(first, new ItemStack(Items.APPLE));
                                require(!recipe.matches(grid.asCraftInput(), world), "Wrong material accepted: " + id);
                                grid.setItem(first, input);

                                if (empty >= 0) {
                                    grid.setItem(empty, input.copy());
                                    require(!recipe.matches(grid.asCraftInput(), world), "Extra material accepted: " + id);
                                    grid.setItem(empty, ItemStack.EMPTY);
                                }
                                if (recipe instanceof CuttingRecipe) {
                                    CompoundTag tag = new CompoundTag();

                                    tag.putString("custom_label", "Saved vertical piece");
                                    tag.putLong("custom_number", 1234567890123L);
                                    zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.setCustom(input, tag);

                                    ItemStack named = expected.copy();

                                    zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.setCustom(named, tag.copy());
                                    assertManager(grid, world, named, "Rotation keeps the complete custom tag");

                                    ItemStack result = recipe.assemble(grid.asCraftInput(), world.registryAccess());

                                    CompoundTag resultData = NativeProbeItems.custom(result);
                                    resultData.putString("custom_label", "Changed result");
                                    NativeProbeItems.setCustom(result, resultData);
                                    require(zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(input).equals(tag), "Rotation result must not share the input tag");
                                }

                                variants++;
                            }
                        }
                    }
                }

                network(recipe, id, world);
                recipes++;
            }
            return !remaining.hasNext();
        }

        int finish() {
            require(!remaining.hasNext(), "The native recipe audit is incomplete");
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
            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CRAFTING_PASS recipes=" + recipes + " variants=" + variants
                    + " conflicts=0 templates=retired network=true");
            return recipes;
        }
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

    private static void network(CraftingRecipe recipe, ResourceLocation id, ServerLevel world) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                world.registryAccess());

        try {
            RecipeHolder.STREAM_CODEC.encode(buffer, new RecipeHolder<>(id, recipe));
            RecipeHolder<?> restored = RecipeHolder.STREAM_CODEC.decode(buffer);
            require(restored.value() instanceof CraftingRecipe, "Network recipe type changed");
            CraftingRecipe decoded = (CraftingRecipe) restored.value();

            require(restored.id().equals(id) && !decoded.isSpecial(),
                    "Network recipe identity and visibility");
            require(ItemStack.matches(decoded.getResultItem(world.registryAccess()), recipe.getResultItem(world.registryAccess())),
                    "Network result changed: " + id);

            CraftingContainer grid = grid(3);

            int width = recipe instanceof ShapedRecipe ? ((ShapedRecipe) recipe).getRecipeWidth()
                    : recipe.getIngredients().size();
            int height = recipe instanceof ShapedRecipe ? ((ShapedRecipe) recipe).getRecipeHeight() : 1;

            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    Ingredient ingredient = recipe.getIngredients().get(column + row * width);

                    if (ingredient != Ingredient.EMPTY) {
                        grid.setItem(column + row * 3, ingredient.getItems()[0].copy());
                    }
                }
            }

            require(ItemStack.matches(decoded.assemble(grid.asCraftInput(), world.registryAccess()), recipe.assemble(grid.asCraftInput(), world.registryAccess())),
                    "Network assembly changed: " + id);
        } finally {
            buffer.release();
        }
    }

    private static void nativeRecipe(ServerLevel world, String path, Item output, int count) {
        Recipe<?> recipe = world.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("minecraft", path))
                .orElseThrow(() -> new AssertionError("Missing native recipe: " + path)).value();
        CraftingContainer grid = grid(3);
        int width = recipe instanceof ShapedRecipe ? ((ShapedRecipe) recipe).getRecipeWidth() : 3;

        for (int index = 0; index < recipe.getIngredients().size(); index++) {
            Ingredient ingredient = recipe.getIngredients().get(index);

            if (ingredient != Ingredient.EMPTY) {
                grid.setItem(index % width + index / width * 3,
                        ingredient.getItems()[0].copy());
            }
        }

        assertManager(grid, world, new ItemStack(output, count), "native " + path);
    }

    private static void templates(ServerLevel world) {
        for (Item retired : Pieces.RETIRED_TEMPLATES.values()) {
            for (RecipeHolder<?> holder : world.getRecipeManager().getRecipes()) {
                Recipe<?> recipe = holder.value();
                require(recipe.getResultItem(world.registryAccess()).getItem() != retired,
                        "Retired templates must not be craftable");
            }

            CraftingContainer grid = grid(2);
            CompoundTag custom = new CompoundTag();

            custom.putString("custom_label", "Old template");
            custom.putLong("custom_number", 1234567890123L);

            ItemStack input = new ItemStack(retired);

            zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.setCustom(input, custom);
            grid.setItem(3, input);

            ItemStack paper = new ItemStack(Items.PAPER);

            zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.setCustom(paper, custom.copy());
            assertManager(grid, world, paper, "retired template recycling");

            CraftingRecipe recycling = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), world).orElseThrow().value();

            for (ItemStack remainder : recycling.getRemainingItems(grid.asCraftInput())) {
                require(remainder.isEmpty(), "Recycling consumes the retired template");
            }

            grid.setItem(0, new ItemStack(Items.STONE));
            require(!recycling.matches(grid.asCraftInput(), world), "Old templates must not cut new pieces");
        }
    }

    private static void assertManager(CraftingContainer grid, ServerLevel world, ItemStack expected, String label) {
        ItemStack actual = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), world)
                .map(recipe -> recipe.value().assemble(grid.asCraftInput(), world.registryAccess())).orElse(ItemStack.EMPTY);

        require(ItemStack.matches(expected, actual), "Crafting manager selects " + label);

        for (RecipeHolder<CraftingRecipe> recipe : world.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, grid.asCraftInput(), world)) {
            require(ItemStack.matches(expected, recipe.value().assemble(grid.asCraftInput(), world.registryAccess())),
                    "Conflicting recipe for " + label + ": " + recipe.id());
        }
    }

    static CraftingContainer grid(int size) {
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
