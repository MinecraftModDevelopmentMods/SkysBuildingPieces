package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Load a sealed fixture's existing chunk index without recreating gameplay test objects. */
public final class ExistingWorldChecks {
    private final WorldServer world;
    private final List<String> chunks;
    private int next;
    private int settle;

    ExistingWorldChecks(WorldServer world) throws IOException {
        this.world = world;
        chunks = Files.readAllLines(new File("upgrade-index.csv").toPath(), StandardCharsets.UTF_8);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("The saved-world fixture has no indexed chunks");
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            for (int batch = 0; batch < 8 && next < chunks.size(); batch++) {
                String[] point = chunks.get(next++).split(",");
                if (point.length != 2) {
                    throw new IllegalStateException("Malformed saved-world chunk index");
                }
                net.minecraft.world.chunk.Chunk chunk = world.getChunk(
                        Integer.parseInt(point[0]), Integer.parseInt(point[1]));
                world.getChunkProvider().queueUnload(chunk);
            }
            if (next == chunks.size() && ++settle >= 40) {
                net.minecraft.world.storage.ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
                try (PrintWriter out = new PrintWriter(new File(world.getSaveHandler().getWorldDirectory(),
                        "previous-version-upgrade.pass"), "UTF-8")) {
                    out.println("indexed_chunks=" + chunks.size());
                    net.minecraft.world.storage.WorldSavedData state =
                            zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState.get(world);
                    out.println("state=" + state.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                }
                System.out.println("BUILDING_PIECES_EXISTING_WORLD_PASS chunks=" + chunks.size());
                MinecraftForge.EVENT_BUS.unregister(this);
                world.getMinecraftServer().initiateShutdown();
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Saved-world verification was interrupted", failure);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Saved-world reload verification failed", failure);
        }
    }
}
