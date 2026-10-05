package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.*;

/** Build-only stand-in for a later material add-on's independent coverage. */
public final class CoverageContributor {
    private final Map<Chunk,Integer> versions=new WeakHashMap<Chunk,Integer>();
    private int visits;
    @SubscribeEvent(priority=EventPriority.LOW) public void load(ChunkDataEvent.Load event) {
        NBTTagCompound coverage=event.getData().getCompoundTag("Level").getCompoundTag("skysbuildingpieces_coverage");
        int version=coverage.getInteger("later_addon");
        if(version<1){visits++;version=1;}
        versions.put(event.getChunk(),version);
    }
    @SubscribeEvent(priority=EventPriority.LOW) public void save(ChunkDataEvent.Save event) {
        Integer version=versions.get(event.getChunk());if(version==null)return;
        NBTTagCompound level=event.getData().getCompoundTag("Level"),coverage=level.getCompoundTag("skysbuildingpieces_coverage");
        coverage.setInteger("later_addon",version);level.setTag("skysbuildingpieces_coverage",coverage);
    }
    public static void verify(WorldServer world) {
        CoverageContributor contributor=new CoverageContributor();MinecraftForge.EVENT_BUS.register(contributor);
        try {
            Chunk chunk=world.getChunkFromChunkCoords(72,72);NBTTagCompound root=new NBTTagCompound(),level=new NBTTagCompound(),coverage=new NBTTagCompound();
            root.setTag("Level",level);coverage.setInteger("vanilla",1);level.setTag("skysbuildingpieces_coverage",coverage);
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk,root));
            if(contributor.visits!=1)throw new AssertionError("Core coverage must not skip a later add-on");
            NBTTagCompound saved=new NBTTagCompound();saved.setTag("Level",new NBTTagCompound());
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk,saved));
            NBTTagCompound markers=saved.getCompoundTag("Level").getCompoundTag("skysbuildingpieces_coverage");
            if(markers.getInteger("vanilla")!=1||markers.getInteger("later_addon")!=1)throw new AssertionError("Independent markers must survive both save handlers");
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk,saved));
            if(contributor.visits!=1)throw new AssertionError("Later add-on coverage must be reload idempotent");
            System.out.println("BUILDING_PIECES_CONTRIBUTOR_PASS independent load/save/reload");
        } finally {MinecraftForge.EVENT_BUS.unregister(contributor);}
    }
}
