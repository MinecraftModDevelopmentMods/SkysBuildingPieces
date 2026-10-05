package zone.moddev.mc.skysbuildingpieces.content;

import java.util.*;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.*;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.fml.common.registry.GameRegistry;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;

/** Exact-template remainders and explicit material quantities, including legacy item bridges. */
public final class CuttingRecipe implements IRecipe {
    public final String template;
    public CuttingRecipe(String template) { this.template=template; }
    private static final Map<String,String[]> PATTERNS=new LinkedHashMap<String,String[]>();
    static {
        PATTERNS.put("slab",new String[]{"ppp","sss"});
        PATTERNS.put("vertical_slab",new String[]{"ps","ps","ps"});
        PATTERNS.put("step",new String[]{"pp ","ss ","   "});
        PATTERNS.put("corner",new String[]{"p  ","s  ","   "});
        PATTERNS.put("stairs",new String[]{"p  ","pp ","sss"});
        PATTERNS.put("wall",new String[]{" p ","ppp","sss"});
        PATTERNS.put("pane",new String[]{"sps","sps","sps"});
    }
    public static void register() {
        RecipeSorter.register("skysbuildingpieces:cutting",CuttingRecipe.class,RecipeSorter.Category.SHAPELESS,"after:minecraft:shapeless");
        for(Map.Entry<String,Item> t:Pieces.TEMPLATES.entrySet()) {
            String[] pattern=PATTERNS.get(t.getKey());List<Object> args=new ArrayList<Object>();Collections.addAll(args,pattern);
            args.add('p');args.add(net.minecraft.init.Items.PAPER);args.add('s');args.add(net.minecraft.init.Items.STICK);
            GameRegistry.addRecipe(new ItemStack(t.getValue()),args.toArray());GameRegistry.addRecipe(new CuttingRecipe(t.getKey()));
        }
    }
    private Result find(InventoryCrafting inv) {
        ItemStack templateStack=null;Catalogue.MaterialDef def=null;ItemStack source=null;int count=0;Shape bridge=null;boolean halfSource=false;
        for(int slot=0;slot<inv.getSizeInventory();slot++) {
            ItemStack stack=inv.getStackInSlot(slot);if(stack==null)continue;
            if(stack.getItem()==Pieces.TEMPLATES.get(template)) {if(templateStack!=null)return null;templateStack=stack;continue;}
            if(source!=null && !ItemStack.areItemsEqual(source,stack))return null;
            String legacy=stack.getItem().getRegistryName().toString();
            if(LegacyMapping.owned(legacy)) {
                String mat=stack.hasTagCompound()?LegacyMapping.material(stack.getTagCompound().getString("material")):"";
                def=Catalogue.INSTANCE.materials.get(mat);bridge=LegacyMapping.shape(legacy,0);
                if(def==null || !LegacyMapping.allowedMaterial(mat,bridge))return null;
            } else {
                Catalogue.MaterialDef matched=null;
                for(Catalogue.MaterialDef m:Catalogue.INSTANCE.materials.values()) {
                    net.minecraft.block.state.IBlockState full=Pieces.nativeState(m,"full");
                    ItemStack equivalent=Pieces.stack(full,1);
                    if(equivalent!=null && ItemStack.areItemsEqual(equivalent,stack)) {matched=m;break;}
                    // Smooth stone has no obtainable full-block item in 1.10.
                    // Its native half slabs are accepted at half the normal yield.
                    if(equivalent==null) {
                        ItemStack half=Pieces.stack(Pieces.nativeState(m,"slab"),1);
                        if(half!=null && ItemStack.areItemsEqual(half,stack)){matched=m;halfSource=true;break;}
                    }
                }
                if(matched==null)return null;def=matched;
            }
            source=stack;count++;
        }
        if(templateStack==null || def==null)return null;
        Shape shape=template.equals("step")?Shape.HORIZONTAL_STEP:Shape.named(template);
        if(bridge!=null) {
            if(count!=1 || !(shape==bridge || shape==Shape.HORIZONTAL_STEP && bridge==Shape.VERTICAL_STEP))return null;
            ItemStack output=Pieces.stack(Pieces.state(def.id,bridge,bridge==Shape.SLAB?1:0),1);
            if(output==null)return null;
            NBTTagCompound preserved=source.writeToNBT(new NBTTagCompound());preserved.setString("id",output.getItem().getRegistryName().toString());preserved.setShort("Damage",(short)output.getMetadata());preserved.setByte("Count",(byte)1);
            return new Result(ItemStack.loadItemStackFromNBT(preserved));
        }
        int needed=(shape==Shape.STAIRS||shape==Shape.WALL||shape==Shape.PANE)?6:1;
        int yield=shape==Shape.SLAB||shape==Shape.VERTICAL_SLAB?2:shape==Shape.HORIZONTAL_STEP?4:shape==Shape.CORNER?8:shape==Shape.STAIRS?4:shape==Shape.WALL?6:16;
        if(halfSource)yield/=2;
        if(count!=needed)return null;
        ItemStack output=Pieces.stack(Pieces.state(def.id,shape,shape==Shape.SLAB?1:0),yield);
        return output==null?null:new Result(output);
    }
    public boolean matches(InventoryCrafting inv,World world) { return find(inv)!=null; }
    public ItemStack getCraftingResult(InventoryCrafting inv) { Result r=find(inv);return r==null?null:r.stack.copy(); }
    public int getRecipeSize() { return 7; }
    public ItemStack getRecipeOutput() { return null; }
    public ItemStack[] getRemainingItems(InventoryCrafting inv) {
        ItemStack[] result=new ItemStack[inv.getSizeInventory()];
        for(int i=0;i<result.length;i++) {ItemStack stack=inv.getStackInSlot(i);if(stack!=null && stack.getItem()==Pieces.TEMPLATES.get(template)){result[i]=stack.copy();result[i].stackSize=1;}}
        return result;
    }
    private static final class Result {final ItemStack stack;Result(ItemStack stack){this.stack=stack;}}
}
