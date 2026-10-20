package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Loads only indexed existing chunks in a disposable full-world copy. */
public final class WorldAuditProbe {
    private final net.minecraft.server.MinecraftServer server;
    private final List<int[]> chunks = new ArrayList<int[]>();
    private int next;
    private final boolean reload;
    private final int batch = Integer.getInteger("skysbuildingpieces.auditBatch", 4);
    private int settleTicks;

    public WorldAuditProbe(WorldServer world, boolean reload) throws IOException {
        this.server = world.getMinecraftServer();
        this.reload = reload;

        if (batch < 1 || batch > 8) {
            throw new IllegalArgumentException(
                    "World audit batch must be 1 to 8, matching the migration queue's per-tick drain.");
        }

        int registryCount = 0;
        int highest = 0;

        for (net.minecraft.block.Block block : net.minecraft.block.Block.REGISTRY) {
            registryCount++;
            highest = Math.max(highest, net.minecraft.block.Block.getIdFromBlock(block));
        }

        System.out.println("BUILDING_PIECES_WORLD_REGISTRY registered=" + registryCount + " highest=" + highest
                + " free=" + (4096 - registryCount));

        Path root = world.getSaveHandler().getWorldDirectory().toPath();

        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            Iterator<Path> files = paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).sorted().iterator();

            while (files.hasNext()) {
                Path path = files.next();
                String dimension = root.relativize(path.getParent().getParent()).toString();
                int dim = dimension.isEmpty() ? 0
                        : dimension.equals("DIM-1") ? -1 : dimension.equals("DIM1") ? 1 : Integer.MIN_VALUE;

                if (dim == Integer.MIN_VALUE) {
                    throw new AssertionError("Unexpected fixture dimension " + dimension);
                }

                String[] coords = path.getFileName().toString().split("\\.");
                int regionX = Integer.parseInt(coords[1]);
                int regionZ = Integer.parseInt(coords[2]);

                try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                    for (int chunkEntryIndex = 0; chunkEntryIndex < 1024; chunkEntryIndex++) {
                        if (file.readInt() != 0) {
                            chunks.add(new int[] { dim, regionX * 32 + (chunkEntryIndex & 31),
                                    regionZ * 32 + (chunkEntryIndex >> 5) });
                        }
                    }
                }
            }
        }

        String index = System.getProperty("skysbuildingpieces.auditIndex");

        if (index == null && Files.isRegularFile(root.getParent().resolve("reload-chunk-index.csv"))) {
            index = root.getParent().resolve("reload-chunk-index.csv").toString();
        }
        if (index != null) {
            Set<String> required = new HashSet<String>(
                    Files.readAllLines(Paths.get(index), java.nio.charset.StandardCharsets.UTF_8));

            chunks.removeIf(
                    coordinates -> !required.remove(coordinates[0] + "," + coordinates[1] + "," + coordinates[2]));

            if (!required.isEmpty()) {
                throw new AssertionError("Saved content chunks disappeared: " + required);
            }
        }

        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("BUILDING_PIECES_WORLD_AUDIT_INDEX " + chunks.size());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            for (int entryIndex = 0; entryIndex < batch && next < chunks.size(); entryIndex++) {
                int[] point = chunks.get(next++);
                WorldServer world = server.getWorld(point[0]);
                Chunk chunk = world.getChunk(point[1], point[2]);

                world.getChunkProvider().queueUnload(chunk);
            }
            // A whole-world scan must not outrun the legacy asynchronous save queue.
            if (next % 256 == 0 || next == chunks.size()) {
                net.minecraft.world.storage.ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
            }
            if (next % 2048 == 0) {
                System.out.println("BUILDING_PIECES_WORLD_AUDIT_PROGRESS " + next + " / " + chunks.size());
            }
            if (next == chunks.size()) {
                // Let deferred inventory scans and asynchronous writes settle before checking counters and asking the
                // server to save.
                if (++settleTicks < 400) {
                    return;
                }

                WorldServer world = server.getWorld(0);
                long blocks = 0;
                long items = 0;

                if (LegacyBridge.active()) {
                    LegacyBridge.report(world);

                    PiecesWorldState state = PiecesWorldState.get(world);

                    blocks = state.blocks;
                    items = state.items;

                    if (blocks != 6072) {
                        throw new AssertionError("Qualified vanilla conversion count: " + blocks + " instead of 6072");
                    }
                    if (items != 1170) {
                        throw new AssertionError(
                                "Qualified encountered inventory count: " + items + " instead of 1170");
                    }
                    if (net.minecraftforge.fml.common.Loader.isModLoaded("skysbuildingpiecesbop")) {
                        PiecesWorldState.Totals bop = state.modules.get("biomesoplenty");

                        if (bop == null || bop.blocks != 601 || bop.items != 52) {
                            throw new AssertionError("Qualified BOP totals: "
                                    + (bop == null ? "missing" : bop.blocks + " / " + bop.items));
                        }
                        if (System.getProperty("skysbuildingpieces.auditRetiredBlocks") != null) {
                            PiecesWorldState.Totals retired = state.modules.get(HistoricalFullBlocks.MODULE);
                            long expectedBlocks = Long
                                    .parseLong(System.getProperty("skysbuildingpieces.auditRetiredBlocks"));
                            long expectedItems = Long
                                    .parseLong(System.getProperty("skysbuildingpieces.auditRetiredItems"));

                            if (retired == null || retired.blocks != expectedBlocks || retired.items != expectedItems) {
                                throw new AssertionError("Retired full-rock totals: "
                                        + (retired == null ? "missing" : retired.blocks + " / " + retired.items));
                            }

                            System.out.println("BUILDING_PIECES_RETIRED_WORLD_PASS blocks=" + retired.blocks + " items="
                                    + retired.items + " reload=" + reload);
                        }
                        if (System.getProperty("skysbuildingpieces.auditDecorationBlocks") != null) {
                            PiecesWorldState.Totals decorations = state.modules.get("retired_bop_decorations");
                            long expected = Long
                                    .parseLong(System.getProperty("skysbuildingpieces.auditDecorationBlocks"));

                            if (decorations == null || decorations.blocks != expected || decorations.items != 4) {
                                throw new AssertionError(
                                        "Retired decoration totals: " + (decorations == null ? "missing"
                                                : decorations.blocks + " / " + decorations.items));
                            }

                            System.out.println("BUILDING_PIECES_DECORATION_WORLD_PASS blocks=" + decorations.blocks
                                    + " items=" + decorations.items + " reload=" + reload);
                        }
                    }
                } else {
                    File root = world.getSaveHandler().getWorldDirectory();

                    if (new File(root, "data/skysbuildingpieces_world_state.dat").exists()
                            || new File(root, "serverconfig/skysbuildingpieces-migration-report.txt").exists()) {
                        throw new AssertionError("Default coexistence wrote migration state");
                    }
                }

                Properties result = new Properties();

                result.setProperty("indexed_chunks", Integer.toString(chunks.size()));
                result.setProperty("blocks", Long.toString(blocks));
                result.setProperty("items", Long.toString(items));
                result.setProperty("reload", Boolean.toString(reload));

                try (OutputStream output = new FileOutputStream(new File(world.getSaveHandler().getWorldDirectory(),
                        reload ? "building-pieces-reload-pass.properties" : "building-pieces-fresh-pass.properties"))) {
                    result.store(output, "Disposable world qualification");
                }

                System.out.println(
                        "BUILDING_PIECES_WORLD_AUDIT_PASS blocks=" + blocks + " items=" + items + " reload=" + reload);
                MinecraftForge.EVENT_BUS.unregister(this);
                server.initiateShutdown();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(2, true);
        }
    }
}
