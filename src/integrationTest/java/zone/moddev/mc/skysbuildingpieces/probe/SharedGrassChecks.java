package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Exercises the released grass API with the actual registered piece states. */
public final class SharedGrassChecks {
    private SharedGrassChecks() {
    }

    public static void run(ServerWorld world) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("skysgrassslabs")) {
            return;
        }

        Class<?> api = Class.forName("zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi");
        Method coversSupport = api.getMethod("coversSupport", BlockState.class, IBlockReader.class, BlockPos.class);
        Method tickGrass = api.getMethod("tickGrass", World.class, BlockPos.class, BlockState.class, Random.class);
        BlockPos position = new BlockPos(-500, 220, -500);

        world.setDayTime(6000);

        for (int chunkOffsetX = -1; chunkOffsetX <= 1; chunkOffsetX++) {
            for (int chunkOffsetZ = -1; chunkOffsetZ <= 1; chunkOffsetZ++) {
                world.getChunk((position.getX() >> 4) + chunkOffsetX, (position.getZ() >> 4) + chunkOffsetZ);
            }
        }

        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (!block.palette.group.equals("grass")) {
                continue;
            }
            for (int orientation = 0; orientation < block.palette.shape.states; orientation++) {
                BlockState grass = Pieces.state("minecraft:grass", block.palette.shape, orientation);

                world.removeBlock(position.above(), false);
                world.setBlock(position.below(), Blocks.DIRT.defaultBlockState(), 2);
                world.setBlock(position, grass, 2);

                int cells = block.mask(grass);
                boolean fullFootprint = ((cells | cells >> 4) & 15) == 15;

                require(coversSupport.invoke(null, grass, world, position).equals(fullFootprint),
                        "shared footprint " + block.palette.shape + "/" + orientation);

                // Every spread attempt targets the ground directly beneath this piece.
                Random downward = new Random(0) {
                    @Override
                    public int nextInt(int bound) {
                        return bound == 5 ? 2 : 1;
                    }
                };

                tickGrass.invoke(null, world, position, grass, downward);
                require(world.getBlockState(position.below())
                        .getBlock() == (fullFootprint ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                        "shared grass support " + block.palette.shape + "/" + orientation);
                require(world.getBlockState(position).equals(grass), "shared tick retains orientation");
                checked++;
            }
        }

        require(checked > 0, "registered grass pieces were tested");
        world.removeBlock(position, false);
        world.removeBlock(position.below(), false);
        System.out.println("BUILDING_PIECES_SHARED_GRASS_PASS states=" + checked);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
