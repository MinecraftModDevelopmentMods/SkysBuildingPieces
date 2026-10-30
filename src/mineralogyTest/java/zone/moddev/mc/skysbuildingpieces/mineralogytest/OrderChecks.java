package zone.moddev.mc.skysbuildingpieces.mineralogytest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Real chunk passes over identical rock terraces, visited in opposite orders. */
final class OrderChecks {
    static void run(WorldServer world) throws Exception {
        IBlockState[] rocks = new IBlockState[3];
        String[] names = { "basalt", "gneiss", "quartzite" };
        for (int index = 0; index < names.length; index++) {
            rocks[index] = Block.REGISTRY.getObject(new ResourceLocation("mineralogy", names[index])).getDefaultState();
        }
        for (int level = 1; level <= 3; level++) {
            byte[][] hashes = new byte[2][];
            for (int order = 0; order < 2; order++) {
                int origin = order == 0 ? 128 : 256;
                List<Chunk> chunks = new ArrayList<>();
                for (int chunkZ = 0; chunkZ < 9; chunkZ++) {
                    for (int chunkX = 0; chunkX < 9; chunkX++) {
                        chunks.add(world.getChunk(origin + chunkX, 128 + chunkZ));
                    }
                }
                for (int localZ = 0; localZ < 144; localZ++) {
                    for (int localX = 0; localX < 144; localX++) {
                        int height = 200 + Math.floorMod(localX * 31 + localZ * 17 + (localX >> 3) * (localZ >> 3), 3);
                        Chunk chunk = chunks.get((localX >> 4) + (localZ >> 4) * 9);
                        for (int heightIndex = 190; heightIndex < 210; heightIndex++) {
                            ExtendedBlockStorage section = chunk.getBlockStorageArray()[heightIndex >> 4];
                            if (section == null) {
                                section = new ExtendedBlockStorage(heightIndex & ~15, true);
                                chunk.getBlockStorageArray()[heightIndex >> 4] = section;
                            }
                            section.set(localX & 15, heightIndex & 15, localZ & 15, Blocks.AIR.getDefaultState());
                        }
                        chunk.getBlockStorageArray()[height >> 4].set(localX & 15, height & 15, localZ & 15,
                                rocks[((localX >> 4) + (localZ >> 4)) % rocks.length]);
                        chunk.getHeightMap()[(localX & 15) | ((localZ & 15) << 4)] = height + 1;
                    }
                }
                if (order == 1) {
                    Collections.reverse(chunks);
                }
                for (Chunk chunk : chunks) {
                    SkysTerrainSmoother.engine.smooth(world, chunk, level);
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                int finerPieces = 0;
                for (int localZ = 0; localZ < 144; localZ++) {
                    for (int localX = 0; localX < 144; localX++) {
                        for (int height = 200; height < 205; height++) {
                            IBlockState state = world.getBlockState(new BlockPos((origin << 4) + localX, height,
                                    (128 << 4) + localZ));
                            if (state.getBlock().getRegistryName().toString().startsWith("skysbuildingpieces:mineralogy_")) {
                                finerPieces++;
                            }
                            digest.update((state + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
                RuntimeProbe.require(level == 1 || finerPieces > 0, "The order test did not exercise finer rock shapes");
                hashes[order] = digest.digest();
                if (order == 0 && level == 3) {
                    Chunk sample = chunks.get(40);
                    long started = System.nanoTime();
                    for (int repetition = 0; repetition < 25; repetition++) {
                        SkysTerrainSmoother.engine.smooth(world, sample, level);
                    }
                    System.out.println("MINERALOGY_SMOOTHER_BENCHMARK columns=256 repetitions=25 avg_ns="
                            + (System.nanoTime() - started) / 25);
                }
            }
            RuntimeProbe.require(Arrays.equals(hashes[0], hashes[1]), "Mineralogy generation order differs at level " + level);
            System.out.println("MINERALOGY_GENERATION_ORDER_PASS level=" + level + " chunks=81");
        }
    }

    private OrderChecks() {
    }
}
