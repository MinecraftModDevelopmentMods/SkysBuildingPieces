package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.eventbus.api.*;

/** Build-only stand-in for a later material add-on's independent coverage. */
public final class CoverageContributor {
    private final Map<net.minecraft.world.level.chunk.ChunkAccess, Integer> versions = new WeakHashMap<net.minecraft.world.level.chunk.ChunkAccess, Integer>();
    private int visits;

    @SubscribeEvent(
            priority = EventPriority.LOW)
    public void load(ChunkDataEvent.Load event) {
        CompoundTag coverage = event.getData().getCompound("skysbuildingpieces_coverage");
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

        CompoundTag level = event.getData().getCompound("Level");
        CompoundTag coverage = level.getCompound("skysbuildingpieces_coverage");

        coverage.putInt("later_addon", version);
        level.put("skysbuildingpieces_coverage", coverage);
    }

    public static void verify(ServerLevel world) {
        // Finish any real disk load before counting the synthetic load/save cycle.
        LevelChunk chunk = world.getChunk(72, 72);
        CoverageContributor contributor = new CoverageContributor();

        MinecraftForge.EVENT_BUS.register(contributor);

        try {
            CompoundTag root = new CompoundTag();
            CompoundTag level = new CompoundTag();
            CompoundTag coverage = new CompoundTag();

            root.put("Level", level);
            coverage.putInt("vanilla", 1);
            level.put("skysbuildingpieces_coverage", coverage);
            MinecraftForge.EVENT_BUS
                    .post(new ChunkDataEvent.Load(chunk, level, net.minecraft.world.level.chunk.ChunkStatus.ChunkType.LEVELCHUNK));

            if (contributor.visits != 1) {
                throw new AssertionError("Core coverage must not skip a later add-on");
            }

            CompoundTag saved = new CompoundTag();

            saved.put("Level", new CompoundTag());
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, chunk.getLevel(), saved));

            CompoundTag markers = saved.getCompound("Level").getCompound("skysbuildingpieces_coverage");

            if (markers.getInt("vanilla") != 1 || markers.getInt("later_addon") != 1) {
                throw new AssertionError("Independent markers must survive both save handlers");
            }

            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, saved.getCompound("Level"),
                    net.minecraft.world.level.chunk.ChunkStatus.ChunkType.LEVELCHUNK));

            if (contributor.visits != 1) {
                throw new AssertionError("Later add-on coverage must be reload idempotent");
            }

            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CONTRIBUTOR_PASS independent load/save/reload");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(contributor);
        }
    }
}
