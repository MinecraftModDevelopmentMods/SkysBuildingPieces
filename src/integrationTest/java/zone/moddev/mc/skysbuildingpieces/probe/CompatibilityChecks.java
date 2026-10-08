package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.entity.item.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.*;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.items.*;
import zone.moddev.mc.skysbuildingpieces.content.*;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Live event, handler, player, ender and frame checks with the real legacy mod. */
public final class CompatibilityChecks {
    public static ItemStack legacy(int count) {
        ItemStack stack=new ItemStack(Item.getByNameOrId("buildingbricks:rock_step"),count,327);
        NBTTagCompound tag=new NBTTagCompound();tag.setString("material","minecraft:stone");tag.setString("custom_label","Unrelated NBT");
        NBTTagList enchantments=new NBTTagList();NBTTagCompound enchantment=new NBTTagCompound();enchantment.setShort("id",(short)33);enchantment.setShort("lvl",(short)1);enchantments.appendTag(enchantment);tag.setTag("ench",enchantments);
        stack.setTagCompound(tag);return stack;
    }
    private static void identity(ItemStack stack,int count,boolean convert) {
        require(stack!=null&&stack.stackSize==count,"stack count");
        require(stack.getItem().getRegistryName().getResourceDomain().equals(convert?"skysbuildingpieces":"buildingbricks"),"optional identity");
        require(stack.getTagCompound().getString("custom_label").equals("Unrelated NBT")&&stack.getTagCompound().getTagList("ench",10).tagCount()==1,"complete unrelated NBT");
    }
    public static void run(WorldServer world) throws Exception {
        slabReference();
        boolean convert=LegacyBridge.active();EntityPlayerMP player=FakePlayerFactory.getMinecraft(world);
        player.inventory.setInventorySlotContents(0,legacy(13));player.getInventoryEnderChest().setInventorySlotContents(0,legacy(11));
        MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(player,world));
        identity(player.inventory.getStackInSlot(0),13,convert);identity(player.getInventoryEnderChest().getStackInSlot(0),11,convert);
        player.inventory.setInventorySlotContents(1,legacy(9));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.LoadFromFile(player,world.getSaveHandler().getWorldDirectory(),player.getUniqueID().toString()));
        identity(player.inventory.getStackInSlot(1),9,convert);
        world.getChunkFromBlockCoords(new BlockPos(930,70,930));
        EntityItem drop=new EntityItem(world,930,70,930,legacy(5));require(world.spawnEntity(drop),"dropped entity must actually join the loaded world");identity(drop.getEntityItem(),5,convert);
        BlockPos p=new BlockPos(930,70,932);world.setBlockState(p.north(),Blocks.STONE.getDefaultState(),2);
        EntityItemFrame frame=new EntityItemFrame(world,p,EnumFacing.SOUTH);frame.setDisplayedItem(legacy(1));world.spawnEntity(frame);identity(frame.getDisplayedItem(),1,convert);
        HandlerTile tile=new HandlerTile();tile.setWorld(world);tile.handler.setStackInSlot(0,legacy(17));
        Method scan=MigrationEvents.class.getDeclaredMethod("containers",TileEntity.class);scan.setAccessible(true);scan.invoke(null,tile);identity(tile.handler.getStackInSlot(0),17,convert);
        // The same container reached through several faces is converted only once.
        NBTTagCompound before=tile.handler.serializeNBT();scan.invoke(null,tile);require(before.equals(tile.handler.serializeNBT()),"handler reload idempotence");
        // Sealed item containers expose saved stacks before opening their GUI.
        ItemStack crate=new ItemStack(Blocks.CHEST);NBTTagCompound crateTag=new NBTTagCompound();crateTag.setBoolean("Sealed",true);
        NBTTagList contents=new NBTTagList();contents.appendTag(legacy(7).writeToNBT(new NBTTagCompound()));crateTag.setTag("Items",contents);crate.setTagCompound(crateTag);
        ItemStack migratedCrate=MigrationEvents.convert(crate,world);NBTTagCompound recovered=migratedCrate.getTagCompound().getTagList("Items",10).getCompoundTagAt(0);
        require(migratedCrate.getItem()==crate.getItem()&&migratedCrate.stackSize==1&&migratedCrate.getTagCompound().getBoolean("Sealed"),"container identity and unrelated data");
        require(recovered.getString("id").startsWith(convert?"skysbuildingpieces:":"buildingbricks:"),"nested inventory replacement gating");
        require(recovered.getByte("Count")==7&&recovered.getCompoundTag("tag").equals(legacy(7).getTagCompound()),"nested count and complete custom NBT");
        require(MigrationEvents.convert(migratedCrate,world)==migratedCrate,"nested inventory idempotence");
        net.minecraft.block.state.IBlockState old=Block.getBlockFromName("buildingbricks:rock_step").getStateFromMeta(11);
        world.setBlockState(p,old,2);TileEntity material=world.getTileEntity(p);NBTTagCompound raw=material.writeToNBT(new NBTTagCompound());raw.setString("material","minecraft:stone");raw.setString("custom_tile","Retain me");material.readFromNBT(raw);
        MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(BlockSnapshot.getBlockSnapshot(world,p),Blocks.STONE.getDefaultState(),player,EnumHand.MAIN_HAND));
        require(world.getBlockState(p).getBlock().getRegistryName().getResourceDomain().equals(convert?"skysbuildingpieces":"buildingbricks"),"newly placed replacement gating");
        if(convert)require(world.getTileEntity(p)==null,"successful placement removes material tile");
        // Soil slabs remain exclusively under the independent Grass Slabs policy.
        boolean grassForce=(Boolean)Class.forName("zone.moddev.mc.skysgrassslabs.config.SkysGrassSlabsConfig").getMethod("forceReplaceBuildingBricksSlabs").invoke(null);
        if(convert)LegacyBridge.report(world);
        long beforeBlocks=convert?PiecesWorldState.get(world).blocks:0;
        for(String soil:new String[]{"grass_slab","dirt_slab"})for(int orientation=0;orientation<2;orientation++) {
            BlockPos soilPos=new BlockPos(940+(soil.equals("grass_slab")?0:3)+orientation,75,940);
            net.minecraft.block.state.IBlockState oldSoil=Block.getBlockFromName("buildingbricks:"+soil).getStateFromMeta(orientation);
            world.setBlockState(soilPos,oldSoil,2);
            MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(BlockSnapshot.getBlockSnapshot(world,soilPos),Blocks.STONE.getDefaultState(),player,EnumHand.MAIN_HAND));
            net.minecraft.block.state.IBlockState actualSoil=world.getBlockState(soilPos);
            require(actualSoil.getBlock().getRegistryName().toString().equals((grassForce?"skysgrassslabs:":"buildingbricks:")+soil),"Grass Slabs alone owns horizontal soil mapping");
            require(actualSoil.getBlock().getMetaFromState(actualSoil)==orientation,"Grass Slabs orientation is unchanged");
            world.setBlockToAir(soilPos);
        }
        if(convert)require(PiecesWorldState.get(world).blocks==beforeBlocks,"soil replacements must not advance Building Pieces counters");
        System.out.println("BUILDING_PIECES_GRASS_COEXISTENCE_PASS force="+grassForce);
        // Explicit bridge recipe is available without enabling world replacement.
        net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}},3,3);
        grid.setInventorySlotContents(0,legacy(1));LegacyPieceRecipe recipe=new LegacyPieceRecipe();
        require(recipe.matches(grid,world),"explicit bridge recipe");identity(recipe.getCraftingResult(grid),1,true);
        if(convert) {
            NBTTagCompound chunk=new NBTTagCompound(),level=new NBTTagCompound(),coverage=new NBTTagCompound();chunk.setTag("Level",level);coverage.setInteger("vanilla",1);coverage.setInteger("later_addon",0);level.setTag("skysbuildingpieces_coverage",coverage);
            LegacyBridge.prepareChunk(world,chunk);require(coverage.getInteger("later_addon")==0,"vanilla marker must not claim later addon coverage");
            coverage.setInteger("later_addon",1);LegacyBridge.prepareChunk(world,chunk);require(coverage.getInteger("later_addon")==1,"independent later-module coverage survives");
            CoverageContributor.verify(world);
        }
        drop.setDead();frame.setDead();world.setBlockToAir(p);player.inventory.clear();player.getInventoryEnderChest().clear();
        LegacyBridge.report(world);System.out.println("BUILDING_PIECES_COMPATIBILITY_PASS forced="+convert);
    }
    private static void slabReference() throws Exception {
        // The installed, unmodified legacy mod supplies the reference decision
        // only. Sky items never enter its material catalogue or placement code.
        Class<?> util=Class.forName("com.hea3ven.buildingbricks.core.util.BlockPlacingUtil");
        Method inner=util.getMethod("isInnerRing",EnumFacing.class,double.class,double.class,double.class);
        Method closest=util.getMethod("getClosestFace",EnumFacing.class,double.class,double.class,double.class);
        Method side=util.getMethod("getClosestSide",EnumFacing.class,double.class,double.class,double.class);
        float[] hits={0,.1f,.125f,.219f,.22f,.25f,.5f,.75f,.78f,.781f,.875f,.9f,1};int checks=0;
        for(EnumFacing face:EnumFacing.values())for(float a:hits)for(float b:hits) {
            float x=face.getAxis()==EnumFacing.Axis.X?.5f:a;
            float y=face.getAxis()==EnumFacing.Axis.Y?.5f:b;
            float z=face.getAxis()==EnumFacing.Axis.X?a:face.getAxis()==EnumFacing.Axis.Y?b:.5f;
            Object[] args={face,(double)x,(double)y,(double)z};boolean centre=(Boolean)inner.invoke(null,args);
            EnumFacing nearest=(EnumFacing)closest.invoke(null,args),edge=(EnumFacing)side.invoke(null,args);
            EnumFacing expected=centre?face.getOpposite():face.getAxis()==EnumFacing.Axis.Y?edge:
                    nearest.getAxis()==EnumFacing.Axis.Y?(y>.5f?EnumFacing.UP:EnumFacing.DOWN):edge;
            require(SlabPlacement.side(face,x,y,z)==expected,"legacy slab face/edge reference "+face+" "+a+" "+b);
            EnumFacing vertical=face.getAxis()!=EnumFacing.Axis.Y&&centre?face.getOpposite():edge;
            require(SlabPlacement.verticalSide(face,x,y,z)==vertical,"legacy vertical slab reference");checks++;
        }
        System.out.println("BUILDING_PIECES_SLAB_REFERENCE_PASS "+checks+" clicks against installed BuildingBricks");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static final class HandlerTile extends TileEntity {
        final ItemStackHandler handler=new ItemStackHandler(2);
        public boolean hasCapability(Capability<?> capability,EnumFacing side){return capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY||super.hasCapability(capability,side);}
        public <T>T getCapability(Capability<T> capability,EnumFacing side){return capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler):super.getCapability(capability,side);}
    }
}
