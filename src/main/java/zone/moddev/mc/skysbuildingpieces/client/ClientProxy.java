package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.*;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import zone.moddev.mc.skysbuildingpieces.CommonProxy;
import zone.moddev.mc.skysbuildingpieces.content.*;

public final class ClientProxy extends CommonProxy {
    public void preInit() {
        registerModels("skysbuildingpieces");
        for(java.util.Map.Entry<String,Item> e:Pieces.TEMPLATES.entrySet())ModelLoader.setCustomModelResourceLocation(e.getValue(),0,new ModelResourceLocation("skysbuildingpieces:template_"+e.getKey(),"inventory"));
    }
    public void registerModels(String namespace) {
        for(PieceBlock b:Pieces.BLOCKS.values()) {
            if(!b.palette.namespace.equals(namespace))continue;
            net.minecraft.client.renderer.block.statemap.StateMap.Builder map=new net.minecraft.client.renderer.block.statemap.StateMap.Builder();
            if(b.palette.shape!=zone.moddev.mc.skysbuildingpieces.catalogue.Shape.WALL && b.palette.shape!=zone.moddev.mc.skysbuildingpieces.catalogue.Shape.PANE)map.ignore(PieceBlock.CONNECTIONS);
            if(b.palette.shape!=zone.moddev.mc.skysbuildingpieces.catalogue.Shape.STAIRS)map.ignore(PieceBlock.STAIR_SHAPE);
            if(!b.palette.group.equals("grass")&&!b.palette.group.equals("dirt"))map.ignore(net.minecraft.block.BlockGrass.SNOWY);
            ModelLoader.setCustomStateMapper(b,map.build());
        }
        for(PieceBlock b:Pieces.BLOCKS.values())for(int slot=0;slot<b.palette.materials.size();slot++) {
            if(!b.palette.namespace.equals(namespace))continue;
            int meta=slot*b.palette.shape.states;
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b),meta,new ModelResourceLocation(namespace+":"+new net.minecraft.util.ResourceLocation(b.palette.materials.get(slot)).getResourcePath()+"_"+b.palette.shape.name().toLowerCase(java.util.Locale.ROOT),"inventory"));
        }
    }
    public void init() {
        final BlockColors colors=Minecraft.getMinecraft().getBlockColors();
        for(final PieceBlock b:Pieces.BLOCKS.values())if(b.palette.group.equals("grass")) {
            colors.registerBlockColorHandler((state,world,pos,tint)->tint==0 ? colors.colorMultiplier(Blocks.GRASS.getDefaultState(),world,pos,0) : -1,b);
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler((stack,tint)->tint==0 ? 0x7CBD6B : -1,Item.getItemFromBlock(b));
        }
    }
}
