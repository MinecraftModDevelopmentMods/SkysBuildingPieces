package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Keeps the standalone lifecycle covered even when Grass Slabs is not installed. */
final class StandaloneLifecycleChecks {
    static void run(ServerLevel world) {
        if (ModList.isLoaded("skysgrassslabs")) {
            return;
        }
        List<String> failures = new ArrayList<>();
        int cases = 0;
        world.getServer().getCommands().performPrefixedCommand(
                world.getServer().createCommandSourceStack().withSuppressedOutput(), "time set 6000");
        for (int height : new int[] {world.getMinY() + 24, 90, world.getMaxY() - 24}) {
            BlockPos position = new BlockPos(-500, height, -500);
            for (int chunkX = -1; chunkX <= 1; chunkX++) {
                for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                    world.getChunk((position.getX() >> 4) + chunkX, (position.getZ() >> 4) + chunkZ);
                }
            }
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                for (int offsetY = -1; offsetY <= 3; offsetY++) {
                    for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                        world.setBlock(position.offset(offsetX, offsetY, offsetZ),
                                Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            world.setBlock(position.below(), Blocks.DIRT.defaultBlockState(), 2);
            world.setBlock(position.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            fullLight(world, position);
            if (world.getMaxLocalRawBrightness(position.above()) < 9) {
                throw new AssertionError("The lifecycle audit needs a lit target");
            }
            BlockState dirt = Pieces.state("minecraft:dirt", Shape.HORIZONTAL_STEP, 4);
            BlockState grass = Pieces.state("minecraft:grass", Shape.HORIZONTAL_STEP, 4);
            world.setBlock(position, dirt, 2);
            world.getBlockState(position).randomTick(world, position, new EastNeighbour());
            if (!world.getBlockState(position).is(grass.getBlock())) {
                failures.add("Dirt did not green at height " + height);
            }
            cases++;

            for (var cover : List.of(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState())) {
                world.setBlock(position, grass, 2);
                world.setBlock(position.above(), cover, 2);
                fullLight(world, position);
                world.getBlockState(position).randomTick(world, position, RandomSource.create(1));
                if (!world.getBlockState(position).is(dirt.getBlock())) {
                    failures.add("Grass survived " + cover + " at height " + height);
                }
                cases++;
            }
            world.setBlock(position.above(), Blocks.SNOW.defaultBlockState(), 2);
            world.setBlock(position, grass, 2);
            world.getBlockState(position).randomTick(world, position, RandomSource.create(1));
            if (!world.getBlockState(position).is(grass.getBlock())) {
                failures.add("One snow layer killed grass at height " + height);
            }
            cases++;
            world.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 2);
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Standalone lifecycle audit: " + failures);
        }
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_STANDALONE_LIFECYCLE_PASS cases=" + cases);
    }

    private static void fullLight(ServerLevel world, BlockPos position) {
        DataLayer data = new DataLayer();
        java.util.Arrays.fill(data.getData(), (byte) 0xff);
        var engine = world.getChunkSource().getLightEngine();
        engine.queueSectionData(LightLayer.BLOCK, SectionPos.of(position), data);
        var finished = engine.lightChunk(world.getChunkAt(position), true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        world.getServer().managedBlock(() -> {
            world.getChunkSource().pollTask();
            engine.tryScheduleUpdate();
            return finished.isDone() || System.nanoTime() >= deadline;
        });
        if (!finished.isDone()) {
            throw new AssertionError("Lifecycle fixture lighting did not finish");
        }
        finished.join();
    }

    private static final class EastNeighbour extends net.minecraft.world.level.levelgen.LegacyRandomSource {
        private int horizontalCalls;

        EastNeighbour() {
            super(0);
        }

        @Override
        public int nextInt(int bound) {
            return bound == 5 ? 1 : (horizontalCalls++ & 1) == 0 ? 2 : 1;
        }
    }

    private StandaloneLifecycleChecks() {
    }
}
