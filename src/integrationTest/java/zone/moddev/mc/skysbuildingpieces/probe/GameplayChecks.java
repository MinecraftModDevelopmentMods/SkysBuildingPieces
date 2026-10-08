package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Real world placement and collision checks, isolated from the saved palette grid. */
public final class GameplayChecks {
    public static void run(WorldServer world) {
        EntityPlayerMP player=FakePlayerFactory.getMinecraft(world);player.capabilities.isCreativeMode=false;
        BlockPos p=new BlockPos(-500,90,-500);world.setBlockToAir(p.up());world.setBlockToAir(p);
        int placed=0;
        for(PieceBlock b:Pieces.BLOCKS.values()) {
            ItemStack item=new ItemStack(Item.getItemFromBlock(b),2,0);
            world.setBlockToAir(p);player.setSneaking(false);
            EnumActionResult result=item.getItem().onItemUse(item,player,world,p,EnumHand.MAIN_HAND,EnumFacing.UP,.25f,.1f,.25f);
            require(result==EnumActionResult.SUCCESS&&item.stackSize==1,"own placement and consumption "+b.getRegistryName());
            IBlockState state=world.getBlockState(p);
            if(b.palette.shape==Shape.VERTICAL_SLAB&&Pieces.state(b.palette.materials.get(0),Shape.SLAB,1)!=null) {
                require(state.equals(Pieces.state(b.palette.materials.get(0),Shape.SLAB,1)),"saved vertical item uses regular slab placement");
                state=b.getStateFromMeta(0);world.setBlockState(p,state,2);
            }else require(state.getBlock()==b,"own placement bypasses legacy catalogue");
            require(world.getTileEntity(p)==null,"pieces never need tiles");
            List<AxisAlignedBB> boxes=b.boxes(b.getActualState(state,world,p));require(!boxes.isEmpty(),"collision boxes");
            Catalogue.MaterialDef definition=b.definition(state);IBlockState nativeFull=Pieces.nativeState(definition,"full");
            require(b.getBlockHardness(state,world,p)==(definition.nativeProperties()?nativeFull.getBlock().getBlockHardness(nativeFull,world,p):definition.hardness()),"material hardness");
            require(b.getSoundType(state,world,p,player)!=null,"material sound");
            for(EnumFacing face:EnumFacing.values())require(b.shouldSideBeRendered(state,world,p,face),"partial face remains visible");
            placed++;
        }
        // Two complementary glass slabs become glass, and consume just one item.
        world.setBlockState(p,Pieces.state("minecraft:glass",Shape.SLAB,1),2);
        ItemStack slab=Pieces.stack(Pieces.state("minecraft:glass",Shape.SLAB,0),2);
        require(slab.getItem().onItemUse(slab,player,world,p,EnumHand.MAIN_HAND,EnumFacing.UP,.25f,.75f,.25f)==EnumActionResult.SUCCESS,"matching slabs combine");
        require(world.getBlockState(p).getBlock()==Blocks.GLASS&&slab.stackSize==1,"normalized full block without duplication");
        world.setBlockState(p,Pieces.state("minecraft:glass",Shape.SLAB,0),2);slab=Pieces.stack(Pieces.state("minecraft:glass",Shape.SLAB,1),2);
        require(slab.getItem().onItemUse(slab,player,world,p,EnumHand.MAIN_HAND,EnumFacing.DOWN,.25f,.5f,.25f)==EnumActionResult.SUCCESS&&world.getBlockState(p).getBlock()==Blocks.GLASS&&slab.stackSize==1,"top slab combines on its lower face");
        for(int o=0;o<4;o++) {
            world.setBlockState(p,Pieces.state("minecraft:glass",Shape.VERTICAL_SLAB,o),2);
            ItemStack vertical=Pieces.stack(Pieces.state("minecraft:glass",Shape.VERTICAL_SLAB,0),2);
            EnumFacing face=new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST}[o];
            float x=face.getAxis()==EnumFacing.Axis.X?.5f:.25f,z=face.getAxis()==EnumFacing.Axis.Z?.5f:.25f;
            require(vertical.getItem().onItemUse(vertical,player,world,p,EnumHand.MAIN_HAND,face,x,.25f,z)==EnumActionResult.SUCCESS&&world.getBlockState(p).getBlock()==Blocks.GLASS&&vertical.stackSize==1,"vertical slab internal boundary "+o);
        }
        // Unlike materials must not replace an occupied piece.
        for(int o=0;o<4;o++) {
            IBlockState dirt=Pieces.state("minecraft:dirt",Shape.VERTICAL_SLAB,o);world.setBlockState(p,dirt,2);
            world.setBlockState(p.north(),Blocks.SNOW_LAYER.getDefaultState(),2);
            require(dirt.getBlock().getActualState(dirt,world,p).getValue(BlockGrass.SNOWY),"vertical dirt snow cap "+o);world.setBlockToAir(p.north());
            ItemStack vertical=Pieces.stack(dirt,2);EnumFacing face=new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST}[o];
            float x=face.getAxis()==EnumFacing.Axis.X?.5f:.25f,z=face.getAxis()==EnumFacing.Axis.Z?.5f:.25f;
            require(vertical.getItem().onItemUse(vertical,player,world,p,EnumHand.MAIN_HAND,face,x,.25f,z)==EnumActionResult.SUCCESS&&world.getBlockState(p).equals(Blocks.DIRT.getDefaultState())&&vertical.stackSize==1,"vertical dirt full-block combination "+o);
        }
        IBlockState original=Pieces.state("minecraft:glass",Shape.SLAB,1);world.setBlockState(p,original,2);world.setBlockState(p.up(),Blocks.STONE.getDefaultState(),2);
        ItemStack other=Pieces.stack(Pieces.state("minecraft:ice",Shape.SLAB,0),2);
        other.getItem().onItemUse(other,player,world,p,EnumHand.MAIN_HAND,EnumFacing.UP,.25f,.75f,.25f);
        require(world.getBlockState(p).equals(original)&&other.stackSize==2,"unlike combination rejected unchanged");
        slabPlacement(world,p,player);
        nativeSlabs(world,p,player);
        world.setBlockState(p.up(),Blocks.STONE.getDefaultState(),2);
        // Grass shapes decay to dirt without changing their octant geometry.
        for(Shape shape:new Shape[]{Shape.HORIZONTAL_STEP,Shape.VERTICAL_STEP,Shape.CORNER,Shape.STAIRS})for(int o=0;o<shape.states;o++) {
            IBlockState grass=Pieces.state("minecraft:grass",shape,o);world.setBlockState(p,grass,2);
            grass.getBlock().updateTick(world,p,grass,new Random(0));
            require(world.getBlockState(p).equals(Pieces.state("minecraft:dirt",shape,o)),"covered grass decay preserves geometry");
        }
        world.setBlockToAir(p.up());world.setBlockState(p.north(),Blocks.SNOW_LAYER.getDefaultState(),2);
        IBlockState grass=Pieces.state("minecraft:grass",Shape.CORNER,0);world.setBlockState(p,grass,2);
        require(grass.getBlock().getActualState(grass,world,p).getValue(BlockGrass.SNOWY),"adjacent snow visual");
        world.setBlockToAir(p.north());require(!grass.getBlock().getActualState(grass,world,p).getValue(BlockGrass.SNOWY),"snow visual clears");
        world.setBlockToAir(p);System.out.println("BUILDING_PIECES_GAMEPLAY_PASS "+placed+" palettes");
        RecipeChecks.run(world);
        materialPhysics(world,p,player);
    }
    private static void slabPlacement(WorldServer world,BlockPos p,EntityPlayerMP player) {
        int checked=0;
        // Real ItemBlock calls against a solid neighbour: centre plus four edges,
        // all six faces, every custom horizontal slab material and both sneak modes.
        for(PieceBlock block:Pieces.BLOCKS.values())if(block.palette.shape==Shape.SLAB)
            for(int slot=0;slot<block.palette.materials.size();slot++)for(EnumFacing face:EnumFacing.values())
                for(int click=0;click<5;click++)for(boolean sneak:new boolean[]{false,true}) {
                    clearAround(world,p);player.setSneaking(sneak);
                    BlockPos anchor=p.offset(face.getOpposite());world.setBlockState(anchor,Blocks.STONE.getDefaultState(),2);
                    float[] edge={.5f,.1f,.9f,.5f,.5f},height={.5f,.5f,.5f,.1f,.9f};
                    float x,y,z;EnumFacing expected;
                    if(face.getAxis()==EnumFacing.Axis.Y) {
                        x=edge[click];z=height[click];y=face==EnumFacing.UP?1:0;
                        expected=new EnumFacing[]{face.getOpposite(),EnumFacing.WEST,EnumFacing.EAST,EnumFacing.NORTH,EnumFacing.SOUTH}[click];
                    } else if(face.getAxis()==EnumFacing.Axis.X) {
                        x=face==EnumFacing.EAST?1:0;y=height[click];z=edge[click];
                        expected=new EnumFacing[]{face.getOpposite(),EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.DOWN,EnumFacing.UP}[click];
                    } else {
                        x=edge[click];y=height[click];z=face==EnumFacing.SOUTH?1:0;
                        expected=new EnumFacing[]{face.getOpposite(),EnumFacing.WEST,EnumFacing.EAST,EnumFacing.DOWN,EnumFacing.UP}[click];
                    }
                    ItemStack stack=new ItemStack(Item.getItemFromBlock(block),2,slot*2);
                    require(stack.getItem().onItemUse(stack,player,world,anchor,EnumHand.MAIN_HAND,face,x,y,z)==EnumActionResult.SUCCESS,"slab face/edge placement "+face+" "+click);
                    require(world.getBlockState(p).equals(Pieces.state(block.palette.materials.get(slot),slabShape(expected),slabOrientation(expected))),"slab selects correct half "+face+" "+click);
                    require(stack.stackSize==1&&world.getBlockState(anchor).getBlock()==Blocks.STONE,"one item consumed; supporting block intact");
                    checked++;
                }
        player.setSneaking(false);
        // A horizontal-slab item also fills the missing half of a vertical slab.
        for(Shape held:new Shape[]{Shape.SLAB,Shape.VERTICAL_SLAB})for(EnumFacing occupied:EnumFacing.values()) {
            clearAround(world,p);
            world.setBlockState(p,Pieces.state("minecraft:glass",slabShape(occupied),slabOrientation(occupied)),2);
            ItemStack stack=Pieces.stack(Pieces.state("minecraft:glass",held,0),2);
            require(stack.getItem().onItemUse(stack,player,world,p,EnumHand.MAIN_HAND,occupied.getOpposite(),.5f,.5f,.5f)==EnumActionResult.SUCCESS,"cross-orientation slab combination");
            require(world.getBlockState(p).getBlock()==Blocks.GLASS&&stack.stackSize==1,"six-way combination normalizes exactly once");
        }
        // If the clicked full block cannot combine, use the neighbour's local
        // coordinates when filling its top slab from below.
        clearAround(world,p);world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        world.setBlockState(p.up(),Pieces.state("minecraft:glass",Shape.SLAB,0),2);
        ItemStack stack=Pieces.stack(Pieces.state("minecraft:glass",Shape.SLAB,1),2);
        require(stack.getItem().onItemUse(stack,player,world,p,EnumHand.MAIN_HAND,EnumFacing.UP,.5f,1,.5f)==EnumActionResult.SUCCESS
                &&world.getBlockState(p.up()).getBlock()==Blocks.GLASS&&stack.stackSize==1,"adjacent slab combination uses destination coordinates");
        clearAround(world,p);world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),2);
        stack=Pieces.stack(Pieces.state("minecraft:glass",Shape.SLAB,1),2);
        player.capabilities.allowEdit=false;
        try {
            require(stack.getItem().onItemUse(stack,player,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5f,1,.5f)==EnumActionResult.FAIL
                    &&world.isAirBlock(p)&&stack.stackSize==2,"denied placement does not change the world or consume a slab");
        } finally {player.capabilities.allowEdit=true;}
        net.minecraft.entity.item.EntityArmorStand stand=new net.minecraft.entity.item.EntityArmorStand(world);
        stand.setPosition(p.getX()+.25,p.getY(),p.getZ()+.5);require(world.spawnEntity(stand),"collision fixture joins world");
        try {
            require(stack.getItem().onItemUse(stack,player,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.1f,1,.5f)==EnumActionResult.FAIL
                    &&world.isAirBlock(p)&&stack.stackSize==2,"occupied selected half rejects placement without consumption");
            stand.setPosition(p.getX()+.75,p.getY(),p.getZ()+.5);
            require(stack.getItem().onItemUse(stack,player,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.1f,1,.5f)==EnumActionResult.SUCCESS
                    &&world.getBlockState(p).equals(Pieces.state("minecraft:glass",Shape.VERTICAL_SLAB,1))&&stack.stackSize==1,"empty selected half permits placement next to an entity");
        } finally {stand.setDead();world.removeEntity(stand);}
        clearAround(world,p);System.out.println("BUILDING_PIECES_SLAB_PLACEMENT_PASS "+checked+" face/edge/sneak cases and 12 combinations");
    }
    private static Shape slabShape(EnumFacing occupied) {return occupied.getAxis()==EnumFacing.Axis.Y?Shape.SLAB:Shape.VERTICAL_SLAB;}
    private static int slabOrientation(EnumFacing occupied) {return occupied==EnumFacing.UP?0:occupied==EnumFacing.DOWN?1:occupied.getHorizontalIndex();}
    private static void clearAround(WorldServer world,BlockPos p) {
        world.setBlockToAir(p);for(EnumFacing face:EnumFacing.values())world.setBlockToAir(p.offset(face));
    }
    private static net.minecraft.inventory.InventoryCrafting grid() {
        return new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}},3,3);
    }
    private static void materialPhysics(WorldServer world,BlockPos p,EntityPlayerMP player) {
        for(PieceBlock b:Pieces.BLOCKS.values())for(int slot=0;slot<b.palette.materials.size();slot++)for(int o=0;o<b.palette.shape.states;o++) {
            IBlockState state=b.getStateFromMeta(slot*b.palette.shape.states+o);Catalogue.MaterialDef def=b.definition(state);world.setBlockState(p,state,2);
            require(b.getHarvestTool(state)==null||b.getHarvestTool(state).equals(def.type.equals("rock")||def.type.equals("metal")?"pickaxe":def.type.equals("wood")?"axe":"shovel"),"harvest class");
            IBlockState nativeFull=Pieces.nativeState(def,"full");int actualFire=b.getFlammability(world,p,EnumFacing.UP),actualSpread=b.getFireSpreadSpeed(world,p,EnumFacing.UP);
            world.setBlockState(p,nativeFull,2);require(actualFire==nativeFull.getBlock().getFlammability(world,p,EnumFacing.UP),"native flammability "+def.id+" actual="+actualFire+" expected="+nativeFull.getBlock().getFlammability(world,p,EnumFacing.UP));
            require(actualSpread==nativeFull.getBlock().getFireSpreadSpeed(world,p,EnumFacing.UP),"native fire spread");world.setBlockState(p,state,2);
            require(ItemStack.areItemStacksEqual(b.getPickBlock(state,null,world,p,player),expectedDrop(def.id,b,state)),"pick identity follows regular slab");
            try {
                java.lang.reflect.Method silk;
                try { silk=PieceBlock.class.getDeclaredMethod("getSilkTouchDrop",IBlockState.class); }
                catch(NoSuchMethodException packaged) { silk=PieceBlock.class.getDeclaredMethod("func_180643_i",IBlockState.class); }
                silk.setAccessible(true);
                require(ItemStack.areItemStacksEqual((ItemStack)silk.invoke(b,state),expectedDrop(def.id,b,state)),"Silk Touch follows regular slab");
            }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            List<ItemStack> drops=b.getDrops(world,p,state,0);
            if(def.normalDrop().isEmpty())require(drops.isEmpty(),"fragile material normal drops");
            else require(drops.size()==1&&ItemStack.areItemStacksEqual(drops.get(0),expectedDrop(def.normalDrop(),b,Pieces.state(def.normalDrop(),b.palette.shape,o))),"material drop and metadata");
            if(def.soil())for(Block plant:new Block[]{Blocks.TALLGRASS,Blocks.SAPLING,Blocks.RED_MUSHROOM}) {
                IBlockState full=Pieces.nativeState(def,"full");
                boolean nativeSupport=full.getBlock().canSustainPlant(full,world,p,EnumFacing.UP,(net.minecraftforge.common.IPlantable)plant);
                require(b.canSustainPlant(state,world,p,EnumFacing.UP,(net.minecraftforge.common.IPlantable)plant)==(nativeSupport&&Geometry.completeTop(b.mask(state))),"native plants need complete upper faces "+def.id+" "+b.orientation(state));
            }
        }
        world.setBlockToAir(p);System.out.println("BUILDING_PIECES_MATERIAL_PHYSICS_PASS");
    }
    private static ItemStack expectedDrop(String material,PieceBlock block,IBlockState state) {
        if(block.palette.shape==Shape.VERTICAL_SLAB) {
            IBlockState horizontal=Pieces.state(material,Shape.SLAB,1);
            if(horizontal!=null)return Pieces.stack(horizontal,1);
            Item dirt=Item.getByNameOrId("skysgrassslabs:dirt_slab");
            if(material.equals("minecraft:dirt")&&dirt!=null)return new ItemStack(dirt,1,0);
        }
        return Pieces.stack(state,1);
    }
    private static void nativeSlabs(WorldServer world,BlockPos p,EntityPlayerMP player) {
        int checked=0;
        for(PieceBlock block:Pieces.BLOCKS.values())if(block.palette.shape==Shape.VERTICAL_SLAB) {
            List<ItemStack> creative=new ArrayList<>();
            block.getSubBlocks(Item.getItemFromBlock(block),net.minecraft.creativetab.CreativeTabs.BUILDING_BLOCKS,creative);
            require(creative.isEmpty(),"vertical slabs absent from creative tab");
        }
        Item dirt=Item.getByNameOrId("skysgrassslabs:dirt_slab");
        if(dirt!=null)for(EnumFacing occupied:EnumFacing.HORIZONTALS) {
            clearAround(world,p);world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),2);
            ItemStack stack=new ItemStack(dirt,2,0);
            float x=occupied==EnumFacing.WEST?.1f:occupied==EnumFacing.EAST?.9f:.5f;
            float z=occupied==EnumFacing.NORTH?.1f:occupied==EnumFacing.SOUTH?.9f:.5f;
            require(stack.onItemUse(player,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,x,1,z)==EnumActionResult.SUCCESS&&
                    world.getBlockState(p).equals(Pieces.state("minecraft:dirt",Shape.VERTICAL_SLAB,occupied.getHorizontalIndex()))&&stack.stackSize==1,"regular dirt slab places vertically");
            ++checked;
        }
        clearAround(world,p);world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),2);
        ItemStack oak=new ItemStack(Blocks.WOODEN_SLAB,2,0);
        PlacementVeto veto=new PlacementVeto();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);
        try {
            require(oak.onItemUse(player,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.1f,1,.5f)!=EnumActionResult.SUCCESS&&
                    veto.seen&&world.isAirBlock(p)&&oak.stackSize==2,"Forge placement protection rolls back the exact native slab placement");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);}
        clearAround(world,p);world.setBlockState(p,Blocks.TRAPDOOR.getDefaultState(),2);
        player.setHeldItem(EnumHand.MAIN_HAND,oak);player.setSneaking(false);
        require(player.interactionManager.processRightClickBlock(player,world,oak,EnumHand.MAIN_HAND,p,EnumFacing.UP,.1f,1,.5f)==EnumActionResult.SUCCESS&&
                world.isAirBlock(p.up())&&oak.stackSize==2&&world.getBlockState(p).getValue(BlockTrapDoor.OPEN),"block activation takes priority and happens exactly once");
        player.setHeldItem(EnumHand.MAIN_HAND,null);
        if(net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks"))return;
        for(Catalogue.MaterialDef material:Catalogue.INSTANCE.materials.values()) {
            if(Pieces.state(material.id,Shape.VERTICAL_SLAB,0)==null)continue;
            IBlockState nativeSlab=Pieces.nativeState(material,"slab");
            if(nativeSlab==null)continue;
            for(EnumHand hand:EnumHand.values())for(EnumFacing occupied:EnumFacing.values()) {
                clearAround(world,p);BlockPos anchor=p.down();world.setBlockState(anchor,Blocks.STONE.getDefaultState(),2);
                float x=occupied==EnumFacing.WEST?.1f:occupied==EnumFacing.EAST?.9f:.5f;
                float z=occupied==EnumFacing.NORTH?.1f:occupied==EnumFacing.SOUTH?.9f:.5f;
                EnumFacing face=occupied==EnumFacing.UP?EnumFacing.DOWN:EnumFacing.UP;
                if(face==EnumFacing.DOWN){anchor=p.up();world.setBlockState(anchor,Blocks.STONE.getDefaultState(),2);}
                ItemStack stack=Pieces.stack(nativeSlab,3);net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setString("custom_label","Preserved slab");stack.setTagCompound(tag);
                require(stack.onItemUse(player,world,anchor,hand,face,x,face==EnumFacing.UP?1:0,z)==EnumActionResult.SUCCESS,"native slab hook returns success "+material.id);
                require(world.getBlockState(p).equals(Pieces.state(material.id,slabShape(occupied),slabOrientation(occupied))),"native slab selects six orientations "+material.id);
                require(stack.stackSize==2&&tag.equals(stack.getTagCompound()),"native hook consumes once and preserves NBT");
                require(stack.onItemUse(player,world,p,hand,occupied.getOpposite(),.5f,.5f,.5f)==EnumActionResult.SUCCESS,"native slab combines");
                require(world.getBlockState(p).equals(Pieces.nativeState(material,"full"))&&stack.stackSize==1,"native combination makes exact full material");
                ++checked;
            }
        }
        clearAround(world,p);player.setSneaking(false);
        System.out.println("BUILDING_PIECES_NATIVE_SLABS_PASS cases="+checked+" creative=hidden drops=regular");
    }
    public static final class PlacementVeto {
        boolean seen;
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void place(net.minecraftforge.event.world.BlockEvent.PlaceEvent event){seen=true;event.setCanceled(true);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
