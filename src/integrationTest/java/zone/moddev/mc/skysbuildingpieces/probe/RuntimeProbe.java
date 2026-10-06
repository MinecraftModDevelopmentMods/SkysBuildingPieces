package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import zone.moddev.mc.skysbuildingpieces.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Build-only server probe. Never on ordinary launches or in production jars. */
@Mod(modid="buildingpiecesprobe",name="Building Pieces Runtime Probe",version="1",dependencies="required-after:skysbuildingpieces;after:buildingbricks")
public final class RuntimeProbe {
    @net.minecraftforge.fml.common.SidedProxy(clientSide="zone.moddev.mc.skysbuildingpieces.probe.ClientProbe",serverSide="zone.moddev.mc.skysbuildingpieces.probe.ProbeProxy")
    public static ProbeProxy proxy;
    private static Block legacy;
    private net.minecraft.server.MinecraftServer server;
    private boolean ran;
    @Mod.EventHandler public void pre(FMLPreInitializationEvent e) {
        String phase=System.getProperty("skysbuildingpieces.integrationPhase","");
        if(!net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks") && !phase.startsWith("legacy-") && !phase.startsWith("client") && !phase.startsWith("sylvester")) {
            legacy=new Block(Material.ROCK).setRegistryName("buildingbricks","rock_step");GameRegistry.register(legacy);
            GameRegistry.register(new ItemBlock(legacy).setRegistryName(legacy.getRegistryName()));
            Block dirt=new Block(Material.GROUND).setRegistryName("buildingbricks","dirt_vertical_slab");GameRegistry.register(dirt);
            GameRegistry.register(new ItemBlock(dirt).setRegistryName(dirt.getRegistryName()));
        } else legacy=Block.getBlockFromName("buildingbricks:rock_step");
        MinecraftForge.EVENT_BUS.register(this);
        proxy.init();
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent e) { server=net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance(); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(server==null||ran||e.phase!=TickEvent.Phase.END)return;ran=true;
        try {
            WorldServer world=server.worldServerForDimension(0);
            world.getGameRules().setOrCreateGameRule("randomTickSpeed","0");
            world.getGameRules().setOrCreateGameRule("doFireTick","false");
            String phase=System.getProperty("skysbuildingpieces.integrationPhase");
            if(phase==null)throw new AssertionError("Unconfigured runtime probe");
            if(phase.startsWith("sylvester")){new WorldAuditProbe(world,phase.equals("sylvester-reload"));return;}
            if(phase.startsWith("bop-legacy-")) {
                Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe.BopLegacyFixture").getMethod("run",WorldServer.class,String.class).invoke(null,world,phase);
                server.initiateShutdown();return;
            }
            GameplayChecks.run(world);
            if(!phase.startsWith("client"))DirtCoverageChecks.run(world);
            if(net.minecraftforge.fml.common.Loader.isModLoaded("skysbuildingpiecesbop")&&!phase.startsWith("client"))
                Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe.BopChecks").getMethod("run",WorldServer.class).invoke(null,world);
            if(phase.startsWith("client-")) {
                int number=0;
                for(PieceBlock b:Pieces.BLOCKS.values())for(int slot=0;slot<b.palette.materials.size();slot++)for(int o=0;o<b.palette.shape.states;o++) {
                    BlockPos p=new BlockPos(500+(number&31),64+((number>>5)&31),500+(number>>10));number++;
                    IBlockState state=b.getStateFromMeta(slot*b.palette.shape.states+o);
                    if(phase.equals("client-reload"))require(world.getBlockState(p).equals(state),"client integrated reload identity");else world.setBlockState(p,state,2);
                }
                ClientStatus.complete=true;System.out.println("BUILDING_PIECES_CLIENT_WORLD_PASS "+phase+" states="+number);return;
            }
            if(phase.equals("compat")){CompatibilityChecks.run(world);server.initiateShutdown();return;}
            if(phase.startsWith("legacy-")) { LegacyFixture.run(world,phase);server.initiateShutdown();return; }
            for(int meta=0;meta<16;meta++) {
                NBTTagCompound root=new NBTTagCompound(),level=new NBTTagCompound(),section=new NBTTagCompound();root.setTag("Level",level);
                byte[] low=new byte[4096],high=new byte[2048],data=new byte[2048];int id=Block.getIdFromBlock(legacy);low[0]=(byte)id;LegacyBridge.setNibble(high,0,id>>8);LegacyBridge.setNibble(data,0,meta);
                section.setByte("Y",(byte)4);section.setByteArray("Blocks",low);section.setByteArray("Add",high);section.setByteArray("Data",data);
                NBTTagList sections=new NBTTagList();sections.appendTag(section);level.setTag("Sections",sections);
                NBTTagCompound tile=new NBTTagCompound();tile.setString("id","legacyMaterial");tile.setString("material","minecraft:stone");tile.setInteger("x",0);tile.setInteger("y",64);tile.setInteger("z",0);
                NBTTagList tiles=new NBTTagList();tiles.appendTag(tile);level.setTag("TileEntities",tiles);
                LegacyBridge.prepareChunk(world,root);
                int targetId=(section.getByteArray("Blocks")[0]&255)|(LegacyBridge.nibble(section.getByteArray("Add"),0)<<8);
                IBlockState actual=Block.getBlockById(targetId).getStateFromMeta(LegacyBridge.nibble(section.getByteArray("Data"),0));
                require(actual.getBlock() instanceof PieceBlock,"legacy step target");PieceBlock b=(PieceBlock)actual.getBlock();
                require(b.mask(actual)==Geometry.mask(LegacyMapping.shape("buildingbricks:rock_step",meta),LegacyMapping.orientation(LegacyMapping.shape("buildingbricks:rock_step",meta),meta)),"step geometry " + meta);
                require(level.getTagList("TileEntities",10).tagCount()==0,"old tile removal");
                NBTTagCompound copy=root.copy();LegacyBridge.prepareChunk(world,root);require(copy.equals(root),"idempotence");
            }
            NBTTagCompound stack=new NBTTagCompound();stack.setString("id","buildingbricks:rock_step");stack.setByte("Count",(byte)17);stack.setShort("Damage",(short)327);
            NBTTagCompound tag=new NBTTagCompound();tag.setString("material","minecraft:stone");tag.setString("custom","preserved");stack.setTag("tag",tag);
            ItemStack decoded=ItemStack.loadItemStackFromNBT(stack);require(decoded!=null && decoded.getItem().getRegistryName().getResourceDomain().equals("skysbuildingpieces"),"raw item hook active");
            require(decoded.stackSize==17&&decoded.getTagCompound().getString("custom").equals("preserved"),"item count and NBT");
            int ordinal=0;
            for(PieceBlock b:Pieces.BLOCKS.values())for(int slot=0;slot<b.palette.materials.size();slot++)for(int o=0;o<b.palette.shape.states;o++) {
                IBlockState state=b.getStateFromMeta(slot*b.palette.shape.states+o);require(b.getMetaFromState(state)==slot*b.palette.shape.states+o,"metadata roundtrip");
                BlockPos p=new BlockPos(500+(ordinal&63),64+((ordinal>>6)&31),500+(ordinal>>11));ordinal++;
                if(phase.equals("reload"))require(world.getBlockState(p).equals(state),"persisted state " + b.getRegistryName()+" / "+o);
                else {world.setBlockState(p,state,2);require(world.getBlockState(p).equals(state),"real storage");}
            }
            require(Pieces.BLOCKS.size()==Catalogue.INSTANCE.palettes.size(),"production budget");
            NBTTagCompound bad=new NBTTagCompound();bad.setString("id","buildingbricks:rock_step");bad.setByte("Count",(byte)3);NBTTagCompound unknown=new NBTTagCompound();unknown.setString("material","missing:unknown");bad.setTag("tag",unknown);
            NBTTagCompound before=bad.copy();boolean stopped=false;try{LegacyBridge.prepareStack(bad);}catch(LegacyBridge.RecoveryAbort expected){stopped=true;}require(stopped&&before.equals(bad),"unknown absent content stops unchanged");
            NBTTagCompound crate=new NBTTagCompound();crate.setString("id","minecraft:chest");crate.setByte("Count",(byte)1);
            NBTTagCompound crateTag=new NBTTagCompound();crateTag.setBoolean("Sealed",true);NBTTagList contents=new NBTTagList();NBTTagCompound nested=stack.copy();nested.setString("id","buildingbricks:rock_step");nested.setShort("Damage",(short)327);contents.appendTag(nested);contents.appendTag(bad.copy());crateTag.setTag("Items",contents);crate.setTag("tag",crateTag);
            before=crate.copy();stopped=false;try{LegacyBridge.prepareStack(crate);}catch(LegacyBridge.RecoveryAbort expected){stopped=true;}
            require(stopped&&before.equals(crate),"nested unknown abort is atomic");
            contents.removeTag(1);LegacyBridge.prepareStack(crate);require(crateTag.getBoolean("Sealed")&&contents.getCompoundTagAt(0).getString("id").startsWith("skysbuildingpieces:"),"sealed nested inventory recovery");
            before=crate.copy();LegacyBridge.prepareStack(crate);require(before.equals(crate),"nested recovery idempotence");
            Properties props=new Properties();File marker=new File(world.getSaveHandler().getWorldDirectory(),"skysbuildingpieces-integration.properties");
            if(marker.isFile())try(InputStream in=new FileInputStream(marker)){props.load(in);}
            props.setProperty(phase+"_complete","true");props.setProperty("gameplay_checks","metadata and storage");props.setProperty("migration_checks","16 step codes, raw item hook, NBT, idempotence");
            try(OutputStream out=new FileOutputStream(marker)){props.store(out,"Runtime verification");}
            System.out.println("BUILDING_PIECES_PROBE_PASS " + phase);server.initiateShutdown();
        }catch(Throwable failure){failure.printStackTrace();net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(2,true);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
