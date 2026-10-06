package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Genuine BuildingBricks save/reload probe; no production dependency. */
public final class LegacyFixture {
    private static final String[] IDS={"rock_step","wood_vertical_slab","glass_slab","dirt_stairs","wood_corner","rock_wall","ice_pane"};
    private static final String[] MATERIALS={"minecraft:stone","minecraft:planks_oak","minecraft:glass","minecraft:dirt","minecraft:planks_spruce","minecraft:stonebrick","minecraft:ice"};
    private static BlockPos pos(int i) {return new BlockPos(800+(i&15),70,800+(i>>4));}
    public static void run(WorldServer world,String phase) throws Exception {
        boolean source=phase.equals("legacy-source"),converted=phase.equals("legacy-converted")||phase.equals("legacy-reload")||phase.equals("legacy-disabled-after");
        if(source) {
            require(!LegacyBridge.active(),"source must leave old content alone");
            for(int i=0;i<26;i++) {
                int family=i<16?0:i-15,meta=i<16?i:family==1?3:0;
                String legacyId=i<22?IDS[family]:"dirt_vertical_slab";String material=i<22?MATERIALS[family]:"minecraft:dirt";if(i>=22)meta=i-22;
                Block old=Block.getBlockFromName("buildingbricks:"+legacyId);require(old!=null,"real legacy block "+legacyId);
                world.setBlockState(pos(i),old.getStateFromMeta(meta),2);
                TileEntity tile=world.getTileEntity(pos(i));require(tile!=null,"real material tile "+legacyId);
                NBTTagCompound n=tile.writeToNBT(new NBTTagCompound());n.setString("material",material);n.setString("fixture_note","material identity, not item damage");tile.readFromNBT(n);tile.markDirty();
            }
            world.setBlockState(pos(32),Blocks.CHEST.getDefaultState(),2);
            TileEntityChest chest=(TileEntityChest)world.getTileEntity(pos(32));chest.setInventorySlotContents(0,legacyStack("rock_step","minecraft:stone",23));
            chest.setInventorySlotContents(1,legacyStack("wood_corner","minecraft:planks_oak",7));
            world.spawnEntity(new EntityItem(world,804,72,804,legacyStack("rock_step","minecraft:stone",5)));
        } else {
            for(int i=0;i<26;i++) {
                int family=i<16?0:i-15,meta=i<16?i:family==1?3:0;
                IBlockState state=world.getBlockState(pos(i));
                String legacyId=i<22?IDS[family]:"dirt_vertical_slab";String material=i<22?MATERIALS[family]:"minecraft:dirt";if(i>=22)meta=i-22;
                if(converted) {
                    Shape shape=LegacyMapping.shape("buildingbricks:"+legacyId,meta);
                    IBlockState expected=Pieces.state(material,shape,LegacyMapping.orientation(shape,meta));
                    require(state.equals(expected),"converted identity and geometry "+i+" "+state+" != "+expected);
                    require(world.getTileEntity(pos(i))==null,"no remaining material tile "+i);
                } else {
                    require(state.getBlock().getRegistryName().toString().equals("buildingbricks:"+legacyId),"default coexistence block "+i);
                    TileEntity tile=world.getTileEntity(pos(i));require(tile!=null&&tile.writeToNBT(new NBTTagCompound()).getString("material").equals(material),"default material identity");
                }
            }
            TileEntityChest chest=(TileEntityChest)world.getTileEntity(pos(32));ItemStack stack=chest.getStackInSlot(0);
            require(stack!=null&&stack.stackSize==23&&stack.getTagCompound().getString("custom_label").equals("NBT survives"),"container count and custom NBT");
            require(stack.getItem().getRegistryName().getResourceDomain().equals(converted?"skysbuildingpieces":"buildingbricks"),"container identity");
            NBTTagCompound saved=new NBTTagCompound();world.getChunkFromBlockCoords(pos(0)).getWorld().getMapStorage().saveAllData();
            if(converted) {PiecesWorldState state=PiecesWorldState.get(world);require(state.blocks==26,"block counter "+state.blocks);require(state.items==35,"item counter "+state.items);}
        }
        Properties result=new Properties();result.setProperty("phase",phase);result.setProperty("forced",Boolean.toString(SkysBuildingPieces.forceReplace));result.setProperty("blocks","26");
        try(OutputStream out=new FileOutputStream(new File(world.getSaveHandler().getWorldDirectory(),phase+".properties"))){result.store(out,"Disposable fixture verification");}
        System.out.println("BUILDING_PIECES_LEGACY_PASS "+phase);
    }
    private static ItemStack legacyStack(String id,String material,int count) {
        NBTTagCompound n=new NBTTagCompound();n.setString("id","buildingbricks:"+id);n.setByte("Count",(byte)count);n.setShort("Damage",(short)0);
        NBTTagCompound tag=new NBTTagCompound();tag.setString("material",material);tag.setString("custom_label","NBT survives");n.setTag("tag",tag);return ItemStack.loadItemStackFromNBT(n);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
