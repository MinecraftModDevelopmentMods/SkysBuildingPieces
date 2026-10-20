package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Exercises the real crafting manager, including vanilla and installed add-ons. */
public final class RecipeChecks {
    public static void run(WorldServer world) {
        preflight(world);

        int recombined = 0;

        for (SlabRecombinationRecipe recipe : SlabRecombinationRecipe.recipes()) {
            for (int size : new int[] { 2, 3 }) {
                InventoryCrafting grid = grid(size);

                grid.setInventorySlotContents(size, recipe.slab.copy());
                grid.setInventorySlotContents(size + 1, recipe.slab.copy());
                require(recipe.matches(grid, world), "matching horizontal slabs recombine");
                assertManager(grid, world, recipe.full, "slab recombination " + recipe.slab);
                recombined++;
                grid.setInventorySlotContents(size + 1, new ItemStack(Items.APPLE));
                require(!recipe.matches(grid, world), "unlike slab material rejected");
                grid = grid(size);
                grid.setInventorySlotContents(0, recipe.slab.copy());
                grid.setInventorySlotContents(size, recipe.slab.copy());
                require(!recipe.matches(grid, world), "vertical pair is reserved for native recipes");
            }
        }

        System.out.println("BUILDING_PIECES_RECOMBINATION_PASS checks=" + recombined);

        int variants = 0;
        Set<String> covered = new HashSet<String>();

        for (CuttingRecipe recipe : CuttingRecipe.recipes()) {
            ItemStack expected = recipe.getRecipeOutput();

            require(expected != null && expected.getCount() > 0, "visible recipe output");

            if (recipe.shape == Shape.VERTICAL_SLAB) {
                require(Pieces.state(recipe.material, Shape.SLAB, 1) == null, "no avoidable vertical slab recipe");
            }

            covered.add(recipe.material + "/" + recipe.shape);

            if (recipe.shape == Shape.SLAB && recipe.pattern == CraftingPattern.ROTATE) {
                InventoryCrafting saved = grid(2);
                ItemStack legacyStack = recipe.ingredient.copy();
                NBTTagCompound tag = new NBTTagCompound();

                tag.setString("custom_label", "Saved vertical slab");
                legacyStack.setTagCompound(tag);
                saved.setInventorySlotContents(3, legacyStack);

                ItemStack result = expected.copy();

                result.setTagCompound(tag.copy());
                require(ItemStack.areItemStacksEqual(result, recipe.getCraftingResult(saved)),
                        "saved vertical slab conversion retains NBT");
                assertManager(saved, world, result, "saved vertical slab NBT");
            }
            for (int size : new int[] { 2, 3 }) {
                if (recipe.pattern.width() > size || recipe.pattern.height() > size) {
                    continue;
                }
                for (int blockX = 0; blockX <= size - recipe.pattern.width(); blockX++) {
                    for (int blockY = 0; blockY <= size - recipe.pattern.height(); blockY++) {
                        for (boolean mirror : new boolean[] { false, true }) {
                            InventoryCrafting grid = grid(size);

                            for (int pixelY = 0; pixelY < recipe.pattern.height(); pixelY++) {
                                for (int pixelX = 0; pixelX < recipe.pattern.width(); pixelX++) {
                                    if (recipe.pattern.occupied(mirror ? recipe.pattern.width() - 1 - pixelX : pixelX,
                                            pixelY)) {
                                        grid.setInventorySlotContents((blockX + pixelX) + (blockY + pixelY) * size,
                                                recipe.ingredient.copy());
                                    }
                                }
                            }

                            require(recipe.matches(grid, world),
                                    "offset/mirrored recipe " + recipe.material + " " + recipe.pattern);
                            require(ItemStack.areItemStacksEqual(expected, recipe.getCraftingResult(grid)),
                                    "result identity and yield");
                            assertManager(grid, world, expected,
                                    recipe.material + " " + recipe.shape + " " + recipe.pattern);

                            for (ItemStack remainder : recipe.getRemainingItems(grid)) {
                                require(remainder.isEmpty(), "ingredients consumed, no tools");
                            }

                            int first = -1;
                            int empty = -1;

                            for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
                                if (!grid.getStackInSlot(slot).isEmpty() && first == -1) {
                                    first = slot;
                                }
                                if (grid.getStackInSlot(slot).isEmpty() && empty == -1) {
                                    empty = slot;
                                }
                            }

                            ItemStack original = grid.getStackInSlot(first);

                            original.setCount(17);
                            require(ItemStack.areItemStacksEqual(expected, recipe.getCraftingResult(grid)),
                                    "one unit per occupied slot");
                            require(original.getCount() == 17, "matching does not mutate source");
                            grid.setInventorySlotContents(first, new ItemStack(Items.APPLE));
                            require(!recipe.matches(grid, world), "wrong/mixed material rejected");
                            grid.setInventorySlotContents(first, original);

                            if (empty >= 0) {
                                grid.setInventorySlotContents(empty, recipe.ingredient.copy());
                                require(!recipe.matches(grid, world), "extra material rejected");
                            }

                            variants++;
                        }
                    }
                }
            }
        }
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            for (Shape shape : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB, Shape.HORIZONTAL_STEP,
                    Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS, Shape.WALL, Shape.PANE }) {
                if (Pieces.state(material.id, shape, shape == Shape.SLAB ? 1 : 0) == null) {
                    continue;
                }
                if (shape == Shape.VERTICAL_SLAB && Pieces.state(material.id, Shape.SLAB, 1) != null) {
                    continue;
                }
                if (shape == Shape.VERTICAL_SLAB && material.id.equals("minecraft:dirt")
                        && Item.getByNameOrId("skysgrassslabs:dirt_slab") != null) {
                    continue;
                }
                if (material.block(shape.name().toLowerCase(java.util.Locale.ROOT)) != null) {
                    continue;
                }
                if (shape == Shape.VERTICAL_STEP
                        && ItemStack.areItemsEqual(Pieces.stack(Pieces.state(material.id, Shape.HORIZONTAL_STEP, 0), 1),
                                Pieces.stack(Pieces.state(material.id, Shape.VERTICAL_STEP, 0), 1))) {
                    continue;
                }

                require(covered.contains(material.id + "/" + shape),
                        "every custom piece obtainable " + material.id + " " + shape);
            }
        }

        // Keep vanilla smooth stone slabs, buttons, pressure plates, fences and panes.
        vanilla(world, new ItemStack(Blocks.STONE), new String[] { "XXX" }, new ItemStack(Blocks.STONE_SLAB, 6, 0));
        vanilla(world, new ItemStack(Blocks.STONE), new String[] { "X" }, new ItemStack(Blocks.STONE_BUTTON));
        vanilla(world, new ItemStack(Blocks.STONE), new String[] { "XX" }, new ItemStack(Blocks.STONE_PRESSURE_PLATE));
        vanilla(world, new ItemStack(Blocks.PLANKS), new String[] { "XXX" }, new ItemStack(Blocks.WOODEN_SLAB, 6, 0));
        vanilla(world, new ItemStack(Blocks.GLASS), new String[] { "XXX", "XXX" },
                new ItemStack(Blocks.GLASS_PANE, 16));
        vanilla(world, new ItemStack(Blocks.NETHER_BRICK), new String[] { "XXX", "XXX" },
                new ItemStack(Blocks.NETHER_BRICK_FENCE, 6));

        for (Item legacyItem : Pieces.RETIRED_TEMPLATES.values()) {
            for (IRecipe recipe : net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES
                    .getValuesCollection()) {
                require(recipe.getRecipeOutput() == null || recipe.getRecipeOutput().getItem() != legacyItem,
                        "no template creation");
            }

            InventoryCrafting grid = grid(2);
            ItemStack input = new ItemStack(legacyItem);
            NBTTagCompound nbt = new NBTTagCompound();

            nbt.setString("custom_label", "Old template");
            nbt.setLong("custom_number", 1234567890123L);
            input.setTagCompound(nbt);
            grid.setInventorySlotContents(3, input);

            ItemStack paper = new ItemStack(Items.PAPER);

            paper.setTagCompound(nbt.copy());
            assertManager(grid, world, paper, "retired template recycling");

            for (ItemStack remainder : new LegacyPieceRecipe().getRemainingItems(grid)) {
                require(remainder.isEmpty(), "recycling consumes the retired template");
            }

            grid.setInventorySlotContents(0, new ItemStack(Blocks.STONE));
            require(new LegacyPieceRecipe().getCraftingResult(grid).isEmpty(), "old template no longer cuts");
        }

        System.out.println("BUILDING_PIECES_CRAFTING_PASS recipes=" + CuttingRecipe.recipes().size() + " variants="
                + variants + " modules=" + Catalogue.INSTANCE.modules.size() + " registry_audit="
                + !net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks") + " templates=retired");
    }

    private static void vanilla(WorldServer world, ItemStack input, String[] rows, ItemStack output) {
        InventoryCrafting grid = grid(3);

        for (int blockY = 0; blockY < rows.length; blockY++) {
            for (int blockX = 0; blockX < rows[blockY].length(); blockX++) {
                if (rows[blockY].charAt(blockX) == 'X') {
                    grid.setInventorySlotContents(blockX + blockY * 3, input.copy());
                }
            }
        }

        assertManager(grid, world, output, "native recipe " + input.getItem().getRegistryName());
    }

    private static void assertManager(InventoryCrafting grid, WorldServer world, ItemStack expected, String label) {
        // Installed legacy-mod runs qualify recovery, not competing recipe sets. The complete active registry is
        // qualified only without BuildingBricks.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks")) {
            return;
        }

        require(ItemStack.areItemStacksEqual(expected, CraftingManager.findMatchingResult(grid, world)),
                "crafting manager selects " + label);

        for (IRecipe candidate : net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValuesCollection()) {
            if (candidate.matches(grid, world)) {
                ItemStack actual = candidate.getCraftingResult(grid);

                require(ItemStack.areItemStacksEqual(expected, actual),
                        "conflicting recipe " + label + " / " + candidate.getClass().getName() + " / " + actual);
            }
        }
    }

    private static void preflight(WorldServer world) {
        if (net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks")) {
            return;
        }

        int conflicts = 0;

        for (CuttingRecipe recipe : CuttingRecipe.recipes()) {
            InventoryCrafting grid = grid(3);

            for (int blockY = 0; blockY < recipe.pattern.height(); blockY++) {
                for (int blockX = 0; blockX < recipe.pattern.width(); blockX++) {
                    if (recipe.pattern.occupied(blockX, blockY)) {
                        grid.setInventorySlotContents(blockX + blockY * 3, recipe.ingredient.copy());
                    }
                }
            }
            for (IRecipe candidate : net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES
                    .getValuesCollection()) {
                if (candidate.matches(grid, world)) {
                    ItemStack result = candidate.getCraftingResult(grid);

                    if (!ItemStack.areItemStacksEqual(recipe.getRecipeOutput(), result)) {
                        conflicts++;
                        System.out.println("BUILDING_PIECES_RECIPE_CONFLICT " + recipe.material + " " + recipe.shape
                                + " " + recipe.pattern + " versus " + candidate.getClass().getName() + " => " + result);
                    }
                }
            }
        }

        require(conflicts == 0, "recipe preflight conflicts=" + conflicts);
    }

    private static InventoryCrafting grid(int size) {
        return new InventoryCrafting(new Container() {
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, size, size);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private RecipeChecks() {
    }
}
