package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.RecipeSorter;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;

/** Optional one-item legacy bridges, not part of normal crafting progression. */
public final class LegacyPieceRecipe implements IRecipe {
    public static void register() {
        RecipeSorter.register("skysbuildingpieces:legacy_piece", LegacyPieceRecipe.class,
                RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
        GameRegistry.addRecipe(new LegacyPieceRecipe());
    }
    private ItemStack result(InventoryCrafting inventory) {
        ItemStack source = null;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null) { if (source != null) return null; source = stack; }
        }
        if (source == null) return null;
        ItemStack target;
        if (Pieces.RETIRED_TEMPLATES.containsValue(source.getItem())) target = new ItemStack(Items.PAPER);
        else if(source.getItem() instanceof PieceItem&&((PieceItem)source.getItem()).piece.palette.shape==Shape.VERTICAL_SLAB&&
                ((PieceItem)source.getItem()).piece.palette.material(source.getMetadata()).equals("minecraft:dirt")) {
            target=Pieces.slabDrop("minecraft:dirt",Pieces.state("minecraft:dirt",Shape.VERTICAL_SLAB,0));
            if(ItemStack.areItemsEqual(source,target))return null;
        }
        else {
            String id = source.getItem().getRegistryName().toString();
            if (!LegacyMapping.owned(id) || !source.hasTagCompound()) return null;
            String material = LegacyMapping.material(source.getTagCompound().getString("material"));
            Shape shape = LegacyMapping.shape(id, 0);
            if (!Catalogue.INSTANCE.materials.containsKey(material) || !LegacyMapping.allowedMaterial(material, shape)) return null;
            target = Pieces.stack(Pieces.state(material, shape, shape == Shape.SLAB ? 1 : 0), 1);
            if(shape==Shape.VERTICAL_SLAB&&target!=null)target=Pieces.slabDrop(material,Pieces.state(material,shape,0));
        }
        if (target == null) return null;
        NBTTagCompound preserved = source.writeToNBT(new NBTTagCompound());
        preserved.setString("id", target.getItem().getRegistryName().toString());
        preserved.setShort("Damage", (short) target.getMetadata());
        preserved.setByte("Count", (byte) 1);
        return ItemStack.loadItemStackFromNBT(preserved);
    }
    public boolean matches(InventoryCrafting inventory, World world) { return result(inventory) != null; }
    public ItemStack getCraftingResult(InventoryCrafting inventory) { return result(inventory); }
    public int getRecipeSize() { return 1; }
    public ItemStack getRecipeOutput() { return null; }
    public ItemStack[] getRemainingItems(InventoryCrafting inventory) { return new ItemStack[inventory.getSizeInventory()]; }
}
