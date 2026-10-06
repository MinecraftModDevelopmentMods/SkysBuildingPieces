package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Client resource bake check; never shipped with the mod. */
public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean startedWorld;
    public void init() {if(System.getProperty("skysbuildingpieces.integrationPhase","").startsWith("client"))MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(ran) {
            if(startedWorld&&ClientStatus.complete){System.out.println("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED");mc.shutdown();startedWorld=false;}
            return;
        }
        ran=true;
        try {
            for(net.minecraft.item.Item retired:Pieces.RETIRED_TEMPLATES.values())
                if(retired.getCreativeTab()!=null)throw new AssertionError("Retired template appears in creative inventory");
            int checked=0;
            for(PieceBlock block:Pieces.BLOCKS.values())for(IBlockState state:block.getBlockState().getValidStates()) {
                if(state.getValue(PieceBlock.META)/block.palette.shape.states>=block.palette.materials.size())continue;
                IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
                if(model==mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel())throw new AssertionError("Missing baked model "+state);
                for(EnumFacing face:new EnumFacing[]{null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST})for(BakedQuad quad:model.getQuads(state,face,0))
                    if(quad.getSprite().getIconName().contains("missingno"))throw new AssertionError("Missing sprite "+state);
                checked++;
            }
            int recoveryModels=0;
            for(String name:new String[]{"skysgrassslabs:grass_slab","skysgrassslabs:dirt_slab","skysgrassslabs:path_slab","skysgrassslabs:turf","buildingbricks:grass_slab","buildingbricks:dirt_slab","buildingbrickscompatvanilla:grass_slab"}) {
                net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName(name);
                if(block==null)continue;
                for(IBlockState state:block.getBlockState().getValidStates()) {
                    IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
                    if(model==mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel())throw new AssertionError("Missing recovery model "+state);
                    for(EnumFacing face:new EnumFacing[]{null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST})for(BakedQuad quad:model.getQuads(state,face,0))
                        if(quad.getSprite().getIconName().contains("missingno"))throw new AssertionError("Missing recovery sprite "+state);
                    recoveryModels++;
                }
            }
            try(PrintWriter out=new PrintWriter(new File(mc.mcDataDir,"building-pieces-client-pass.txt"),"UTF-8")){out.println("baked_states="+checked);out.println("recovery_models="+recoveryModels);}
            System.out.println("BUILDING_PIECES_CLIENT_PASS "+checked);
            if(System.getProperty("skysbuildingpieces.integrationPhase").equals("client"))mc.shutdown();
            else {startedWorld=true;mc.launchIntegratedServer("building-pieces-world","Building Pieces Test",new net.minecraft.world.WorldSettings(4815162342L,net.minecraft.world.GameType.CREATIVE,true,false,net.minecraft.world.WorldType.FLAT));}
        }catch(Throwable failure){failure.printStackTrace();net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(2,true);}
    }
}
