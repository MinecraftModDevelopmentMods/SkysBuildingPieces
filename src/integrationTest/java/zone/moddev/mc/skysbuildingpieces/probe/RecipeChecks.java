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
        int variants = 0;
        Set<String> covered = new HashSet<String>();
        for (CuttingRecipe recipe : CuttingRecipe.recipes()) {
            ItemStack expected = recipe.getRecipeOutput();
            require(expected != null && expected.stackSize > 0, "visible recipe output");
            covered.add(recipe.material + "/" + recipe.shape);
            for (int size : new int[]{2, 3}) {
                if (recipe.pattern.width() > size || recipe.pattern.height() > size) continue;
                for (int x = 0; x <= size - recipe.pattern.width(); x++)
                    for (int y = 0; y <= size - recipe.pattern.height(); y++) for (boolean mirror : new boolean[]{false, true}) {
                        InventoryCrafting grid = grid(size);
                        for (int py = 0; py < recipe.pattern.height(); py++) for (int px = 0; px < recipe.pattern.width(); px++)
                            if (recipe.pattern.occupied(mirror ? recipe.pattern.width() - 1 - px : px, py))
                                grid.setInventorySlotContents((x + px) + (y + py) * size, recipe.ingredient.copy());
                        require(recipe.matches(grid, world), "offset/mirrored recipe " + recipe.material + " " + recipe.pattern);
                        require(ItemStack.areItemStacksEqual(expected, recipe.getCraftingResult(grid)), "result identity and yield");
                        assertManager(grid, world, expected, recipe.material + " " + recipe.shape + " " + recipe.pattern);
                        for (ItemStack remainder : recipe.getRemainingItems(grid)) require(remainder == null, "ingredients consumed, no tools");
                        int first = -1, empty = -1;
                        for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
                            if (grid.getStackInSlot(slot) != null && first == -1) first = slot;
                            if (grid.getStackInSlot(slot) == null && empty == -1) empty = slot;
                        }
                        ItemStack original = grid.getStackInSlot(first);
                        original.stackSize = 17;
                        require(ItemStack.areItemStacksEqual(expected, recipe.getCraftingResult(grid)), "one unit per occupied slot");
                        require(original.stackSize == 17, "matching does not mutate source");
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
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values())
            for (Shape shape : new Shape[]{Shape.SLAB, Shape.VERTICAL_SLAB, Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS, Shape.WALL, Shape.PANE}) {
                if (Pieces.state(material.id, shape, shape == Shape.SLAB ? 1 : 0) == null) continue;
                if (material.block(shape.name().toLowerCase(java.util.Locale.ROOT)) != null) continue;
                if (shape == Shape.VERTICAL_STEP && ItemStack.areItemsEqual(
                        Pieces.stack(Pieces.state(material.id, Shape.HORIZONTAL_STEP, 0), 1),
                        Pieces.stack(Pieces.state(material.id, Shape.VERTICAL_STEP, 0), 1))) continue;
                require(covered.contains(material.id + "/" + shape), "every custom piece obtainable " + material.id + " " + shape);
            }
        // Keep vanilla smooth stone slabs, buttons, pressure plates, fences and panes.
        vanilla(world, new ItemStack(Blocks.STONE), new String[]{"XXX"}, new ItemStack(Blocks.STONE_SLAB, 6, 0));
        vanilla(world, new ItemStack(Blocks.STONE), new String[]{"X"}, new ItemStack(Blocks.STONE_BUTTON));
        vanilla(world, new ItemStack(Blocks.STONE), new String[]{"XX"}, new ItemStack(Blocks.STONE_PRESSURE_PLATE));
        vanilla(world, new ItemStack(Blocks.PLANKS), new String[]{"XXX"}, new ItemStack(Blocks.WOODEN_SLAB, 6, 0));
        vanilla(world, new ItemStack(Blocks.GLASS), new String[]{"XXX", "XXX"}, new ItemStack(Blocks.GLASS_PANE, 16));
        vanilla(world, new ItemStack(Blocks.NETHER_BRICK), new String[]{"XXX", "XXX"}, new ItemStack(Blocks.NETHER_BRICK_FENCE, 6));
        for (Item old : Pieces.RETIRED_TEMPLATES.values()) {
            for (IRecipe recipe : CraftingManager.getInstance().getRecipeList())
                require(recipe.getRecipeOutput() == null || recipe.getRecipeOutput().getItem() != old, "no template creation");
            InventoryCrafting grid = grid(2);
            ItemStack input = new ItemStack(old); NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("custom_label", "Old template"); nbt.setLong("custom_number", 1234567890123L); input.setTagCompound(nbt);
            grid.setInventorySlotContents(3, input);
            ItemStack paper = new ItemStack(Items.PAPER); paper.setTagCompound(nbt.copy());
            assertManager(grid, world, paper, "retired template recycling");
            for (ItemStack remainder : new LegacyPieceRecipe().getRemainingItems(grid))
                require(remainder == null, "recycling consumes the retired template");
            grid.setInventorySlotContents(0, new ItemStack(Blocks.STONE));
            require(new LegacyPieceRecipe().getCraftingResult(grid) == null, "old template no longer cuts");
        }
        System.out.println("BUILDING_PIECES_CRAFTING_PASS recipes=" + CuttingRecipe.recipes().size() + " variants=" + variants + " modules=" + Catalogue.INSTANCE.modules.size() + " registry_audit=" + !net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks") + " templates=retired");
    }
    private static void vanilla(WorldServer world, ItemStack input, String[] rows, ItemStack output) {
        InventoryCrafting grid = grid(3);
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++)
            if (rows[y].charAt(x) == 'X') grid.setInventorySlotContents(x + y * 3, input.copy());
        assertManager(grid, world, output, "native recipe " + input.getItem().getRegistryName());
    }
    private static void assertManager(InventoryCrafting grid, WorldServer world, ItemStack expected, String label) {
        // Installed legacy-mod runs qualify recovery, not competing recipe sets.
        // The complete active registry is qualified only without BuildingBricks.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks")) return;
        require(ItemStack.areItemStacksEqual(expected, CraftingManager.getInstance().findMatchingRecipe(grid, world)), "crafting manager selects " + label);
        for (IRecipe candidate : CraftingManager.getInstance().getRecipeList()) if (candidate.matches(grid, world)) {
            ItemStack actual = candidate.getCraftingResult(grid);
            require(ItemStack.areItemStacksEqual(expected, actual), "conflicting recipe " + label + " / " + candidate.getClass().getName() + " / " + actual);
        }
    }
    private static void preflight(WorldServer world) {
        if (net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks")) return;
        int conflicts = 0;
        for (CuttingRecipe recipe : CuttingRecipe.recipes()) {
            InventoryCrafting grid = grid(3);
            for (int y = 0; y < recipe.pattern.height(); y++) for (int x = 0; x < recipe.pattern.width(); x++)
                if (recipe.pattern.occupied(x, y)) grid.setInventorySlotContents(x + y * 3, recipe.ingredient.copy());
            for (IRecipe candidate : CraftingManager.getInstance().getRecipeList()) if (candidate.matches(grid, world)) {
                ItemStack result = candidate.getCraftingResult(grid);
                if (!ItemStack.areItemStacksEqual(recipe.getRecipeOutput(), result)) {
                    conflicts++;
                    System.out.println("BUILDING_PIECES_RECIPE_CONFLICT " + recipe.material + " " + recipe.shape + " " + recipe.pattern + " versus " + candidate.getClass().getName() + " => " + result);
                }
            }
        }
        require(conflicts == 0, "recipe preflight conflicts=" + conflicts);
    }
    private static InventoryCrafting grid(int size) {
        return new InventoryCrafting(new Container() {
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
        }, size, size);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private RecipeChecks() { }
}
