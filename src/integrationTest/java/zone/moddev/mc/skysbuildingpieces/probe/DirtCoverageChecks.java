package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ChunkDataEvent;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** New coverage must revisit old chunks without counting earlier work again. */
public final class DirtCoverageChecks {
    public static void run(WorldServer world) {
        Block old=Block.getBlockFromName("buildingbricks:dirt_vertical_slab");
        if(old==null)return; // Absent-mod packaged fixtures exercise the saved numeric snapshot instead.
        boolean installed=net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks");
        boolean forced=SkysBuildingPieces.forceReplace;
        net.minecraft.world.chunk.Chunk testChunk=world.getChunkFromChunkCoords(90,90);
        PiecesWorldState totals=PiecesWorldState.get(world);long blocks=totals.blocks,chunks=totals.chunks,items=totals.items;
        try {
            if(installed){SkysBuildingPieces.forceReplace=false;NBTTagCompound raw=chunk(old,0,"minecraft:dirt"),before=raw.copy();LegacyBridge.prepareChunk(world,raw);require(before.equals(raw),"replacement off retains dirt and markers");}
            SkysBuildingPieces.forceReplace=true;
            for(int orientation=0;orientation<4;orientation++) {
                NBTTagCompound raw=chunk(old,orientation,"minecraft:dirt");NBTTagCompound level=raw.getCompoundTag("Level"),coverage=level.getCompoundTag("skysbuildingpieces_coverage");
                LegacyBridge.prepareChunk(world,raw);
                NBTTagCompound section=level.getTagList("Sections",10).getCompoundTagAt(0);
                int numeric=(section.getByteArray("Blocks")[0]&255)|(LegacyBridge.nibble(section.getByteArray("Add"),0)<<8);
                IBlockState actual=Block.getBlockById(numeric).getStateFromMeta(LegacyBridge.nibble(section.getByteArray("Data"),0));
                require(actual.equals(Pieces.state("minecraft:dirt",Shape.VERTICAL_SLAB,orientation)),"vertical dirt geometry "+orientation);
                require(coverage.getInteger("vanilla")==1&&coverage.getInteger("biomesoplenty")==1&&coverage.getInteger("future_module")==7&&coverage.getInteger("dirt_vertical_slabs")==1,"independent coverage");
                require(!raw.hasKey("skysbuildingpieces_delta")&&raw.getLong("skysbuildingpieces_additional_vanilla_delta")==1,"new conversions only");
                require(level.getTagList("TileEntities",10).tagCount()==0&&coverage.getTagList("retained_tiles",10).getCompoundTagAt(0).getString("custom").equals("preserved"),"tile data retained after success");
                MigrationEvents events=new MigrationEvents();events.load(new ChunkDataEvent.Load(testChunk,raw));
                require(totals.blocks==blocks+orientation+1&&totals.chunks==chunks,"no duplicate chunk accounting");
                NBTTagCompound before=raw.copy();LegacyBridge.prepareChunk(world,raw);require(before.equals(raw),"coverage reload idempotence");
            }
            NBTTagCompound item=new NBTTagCompound(),tag=new NBTTagCompound();item.setString("id","buildingbricks:dirt_vertical_slab");item.setByte("Count",(byte)9);item.setShort("Damage",(short)30000);tag.setString("material","minecraft:dirt");tag.setString("custom","unchanged");item.setTag("tag",tag);
            LegacyBridge.prepareStack(item);require(item.getString("id").equals("skysbuildingpieces:dirt_vertical_slab_00")&&item.getByte("Count")==9&&item.getCompoundTag("tag").equals(tag),"item identity count and NBT");
            NBTTagCompound before=item.copy();LegacyBridge.prepareStack(item);require(before.equals(item),"item idempotence");
            if(!installed){NBTTagCompound bad=chunk(old,0,"missing:unknown"),copy=bad.copy();boolean stopped=false;try{LegacyBridge.prepareChunk(world,bad);}catch(LegacyBridge.RecoveryAbort expected){stopped=true;}require(stopped&&copy.equals(bad),"unknown dirt material fails before writing");}
        } finally {LegacyBridge.report(world);SkysBuildingPieces.forceReplace=forced;totals.blocks=blocks;totals.chunks=chunks;totals.items=items;totals.markDirty();}
        System.out.println("BUILDING_PIECES_DIRT_COVERAGE_PASS orientations=4 no_repeat_chunk_count=true");
    }
    private static NBTTagCompound chunk(Block old,int meta,String material) {
        NBTTagCompound root=new NBTTagCompound(),level=new NBTTagCompound(),coverage=new NBTTagCompound(),section=new NBTTagCompound(),tile=new NBTTagCompound();root.setTag("Level",level);
        coverage.setInteger("vanilla",1);coverage.setInteger("biomesoplenty",1);coverage.setInteger("future_module",7);level.setTag("skysbuildingpieces_coverage",coverage);
        byte[] low=new byte[4096],high=new byte[2048],data=new byte[2048];int id=Block.getIdFromBlock(old);low[0]=(byte)id;LegacyBridge.setNibble(high,0,id>>8);LegacyBridge.setNibble(data,0,meta);
        section.setByte("Y",(byte)4);section.setByteArray("Blocks",low);section.setByteArray("Add",high);section.setByteArray("Data",data);NBTTagList sections=new NBTTagList();sections.appendTag(section);level.setTag("Sections",sections);
        tile.setString("id","legacyMaterial");tile.setString("material",material);tile.setInteger("x",0);tile.setInteger("z",0);tile.setInteger("y",64);tile.setString("custom","preserved");NBTTagList tiles=new NBTTagList();tiles.appendTag(tile);level.setTag("TileEntities",tiles);return root;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
