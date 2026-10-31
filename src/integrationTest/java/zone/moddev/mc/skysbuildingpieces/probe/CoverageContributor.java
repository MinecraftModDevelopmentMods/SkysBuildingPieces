package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.eventbus.api.*;

/** Build-only stand-in for a later material add-on's independent coverage. */
public final class CoverageContributor {
    private final Map<net.minecraft.world.chunk.IChunk, Integer> versions = new WeakHashMap<net.minecraft.world.chunk.IChunk, Integer>();
    private int visits;

    @SubscribeEvent(
            priority = EventPriority.LOW)
    public void load(ChunkDataEvent.Load event) {
        CompoundNBT coverage = event.getData().getCompound("skysbuildingpieces_coverage");
        int version = coverage.getInt("later_addon");

        if (version < 1) {
            visits++;
            version = 1;
        }

        versions.put(event.getChunk(), version);
    }

    @SubscribeEvent(
            priority = EventPriority.LOW)
    public void save(ChunkDataEvent.Save event) {
        Integer version = versions.get(event.getChunk());

        if (version == null) {
            return;
        }

        CompoundNBT level = event.getData().getCompound("Level");
        CompoundNBT coverage = level.getCompound("skysbuildingpieces_coverage");

        coverage.putInt("later_addon", version);
        level.put("skysbuildingpieces_coverage", coverage);
    }

    public static void verify(ServerWorld world) {
        // Finish any real disk load before counting the synthetic load/save cycle.
        Chunk chunk = world.getChunk(72, 72);
        CoverageContributor contributor = new CoverageContributor();

        MinecraftForge.EVENT_BUS.register(contributor);

        try {
            CompoundNBT root = new CompoundNBT();
            CompoundNBT level = new CompoundNBT();
            CompoundNBT coverage = new CompoundNBT();

            root.put("Level", level);
            coverage.putInt("vanilla", 1);
            level.put("skysbuildingpieces_coverage", coverage);
            MinecraftForge.EVENT_BUS
                    .post(new ChunkDataEvent.Load(chunk, level, net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));

            if (contributor.visits != 1) {
                throw new AssertionError("Core coverage must not skip a later add-on");
            }

            CompoundNBT saved = new CompoundNBT();

            saved.put("Level", new CompoundNBT());
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, saved));

            CompoundNBT markers = saved.getCompound("Level").getCompound("skysbuildingpieces_coverage");

            if (markers.getInt("vanilla") != 1 || markers.getInt("later_addon") != 1) {
                throw new AssertionError("Independent markers must survive both save handlers");
            }

            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, saved.getCompound("Level"),
                    net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));

            if (contributor.visits != 1) {
                throw new AssertionError("Later add-on coverage must be reload idempotent");
            }

            System.out.println("BUILDING_PIECES_CONTRIBUTOR_PASS independent load/save/reload");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(contributor);
        }
    }
}
