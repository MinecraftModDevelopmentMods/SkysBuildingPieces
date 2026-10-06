package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

public final class Pieces {
    public static final Map<String,PieceBlock> BLOCKS=new LinkedHashMap<String,PieceBlock>();
    // Earlier betas may have saved these IDs. They are hidden, cannot be made,
    // and have no cutting behaviour; a one-item recipe recycles them to paper.
    public static final Map<String,Item> RETIRED_TEMPLATES=new LinkedHashMap<String,Item>();
    // Enable the full catalogue only after the compact loader and migration gates pass.
    public static final boolean COMPACT = false;
    public static boolean initialized;
    public static void register() {
        registerModule(Catalogue.INSTANCE.modules.get("vanilla"));
        initialized=true;
        for(String name:new String[]{"slab","vertical_slab","step","corner","stairs","wall","pane"}) {
            Item item=new Item().setMaxStackSize(1).setUnlocalizedName("skysbuildingpieces.template_"+name)
                .setRegistryName("skysbuildingpieces","template_"+name);
            GameRegistry.register(item);RETIRED_TEMPLATES.put(name,item);
        }
    }
    public static void registerModule(Catalogue.Module module) {
        for(Catalogue.MaterialDef definition:module.materials.values())nativeState(definition,"full");
        for(Catalogue.Palette palette:module.palettes) {
            if(COMPACT && !(palette.materials.contains("minecraft:grass") || palette.materials.contains("minecraft:dirt") ||
                    palette.materials.contains("minecraft:glass") || palette.materials.contains("minecraft:stone"))) continue;
            PieceBlock block=new PieceBlock(palette);
            GameRegistry.register(block);
            GameRegistry.register(new PieceItem(block).setRegistryName(block.getRegistryName()));
            BLOCKS.put(palette.module.equals("vanilla")?palette.id:palette.registryId(),block);
            if(palette.group.equals("wood"))net.minecraft.init.Blocks.FIRE.setFireInfo(block,5,20);
            if(palette.group.equals("cloth"))net.minecraft.init.Blocks.FIRE.setFireInfo(block,30,60);
        }
    }
    public static IBlockState nativeState(Catalogue.MaterialDef material,String shape) {
        JsonElement value=material.block(shape);
        if(value==null) return null;
        String id=value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("id").getAsString();
        int meta=value.isJsonObject() && value.getAsJsonObject().has("meta") ? value.getAsJsonObject().get("meta").getAsInt() : 0;
        Block block=Block.REGISTRY.getObject(new ResourceLocation(id));
        if(block==null || block==net.minecraft.init.Blocks.AIR) throw new IllegalStateException("Missing native material " + id);
        return block.getStateFromMeta(meta);
    }
    public static IBlockState state(String material,Shape shape,int orientation) {
        if(shape==Shape.STEP) {shape=orientation>=8?Shape.VERTICAL_STEP:Shape.HORIZONTAL_STEP;orientation=orientation>=8?orientation&3:orientation;}
        Catalogue.MaterialDef def=Catalogue.INSTANCE.materials.get(material);
        if(def==null || !Catalogue.supportsShape(def,shape)) return null;
        IBlockState nativeState=nativeState(def,shape.name().toLowerCase(Locale.ROOT));
        if(nativeState!=null) {
            if(shape==Shape.SLAB) return nativeState.getBlock().getStateFromMeta(nativeState.getBlock().getMetaFromState(nativeState) | (orientation==0 ? 8 : 0));
            if(shape==Shape.STAIRS) return nativeState.getBlock().getStateFromMeta(orientation&7);
            return nativeState;
        }
        for(PieceBlock block:BLOCKS.values()) {
            Shape stored=block.palette.shape;
            int mapped=orientation;
            if(stored==Shape.STEP && (shape==Shape.HORIZONTAL_STEP || shape==Shape.VERTICAL_STEP)) mapped=(shape==Shape.VERTICAL_STEP?8:0)+orientation;
            else if(stored!=shape) continue;
            if(block.palette.materials.contains(material)) return block.getStateFromMeta(block.palette.meta(material,mapped));
        }
        return null;
    }
    public static ItemStack stack(IBlockState state,int count) {
        if(state==null) return null;
        Block block=state.getBlock();
        // A material identity is not its ordinary breaking drop: vanilla
        // podzol, for example, drops dirt but its crafting input is podzol.
        int meta=block instanceof PieceBlock ? ((PieceBlock)block).canonicalMeta(state) :
            block instanceof net.minecraft.block.BlockStairs ? 0 :
            block instanceof net.minecraft.block.BlockSlab ? block.damageDropped(state) : block.getMetaFromState(state);
        Item item=Item.getItemFromBlock(block);
        return item==null ? null : new ItemStack(item,count,meta);
    }
    private Pieces() { }
}
