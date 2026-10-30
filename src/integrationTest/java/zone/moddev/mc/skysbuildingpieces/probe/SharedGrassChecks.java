package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Exercises the released grass API with the actual registered piece states. */
public final class SharedGrassChecks {
    private SharedGrassChecks() {
    }

    public static void run(WorldServer world) throws ReflectiveOperationException {
        if (!Loader.isModLoaded("skysgrassslabs")) {
            return;
        }

        Class<?> api = Class.forName("zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi");
        Method coversSupport = api.getMethod("coversSupport", IBlockState.class, IBlockAccess.class, BlockPos.class);
        Method tickGrass = api.getMethod("tickGrass", World.class, BlockPos.class, IBlockState.class, Random.class);
        BlockPos position = new BlockPos(-500, 220, -500);
        world.setWorldTime(6000);
        for (int chunkOffsetX = -1; chunkOffsetX <= 1; chunkOffsetX++) {
            for (int chunkOffsetZ = -1; chunkOffsetZ <= 1; chunkOffsetZ++) {
                world.getChunk((position.getX() >> 4) + chunkOffsetX,
                        (position.getZ() >> 4) + chunkOffsetZ);
            }
        }

        int checked = 0;
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (!block.palette.group.equals("grass")) {
                continue;
            }
            for (int orientation = 0; orientation < block.palette.shape.states; orientation++) {
                IBlockState grass = Pieces.state("minecraft:grass", block.palette.shape, orientation);
                world.setBlockToAir(position.up());
                world.setBlockState(position.down(), Blocks.DIRT.getDefaultState(), 2);
                world.setBlockState(position, grass, 2);

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
                require(world.getBlockState(position.down()).getBlock()
                                == (fullFootprint ? Blocks.DIRT : Blocks.GRASS),
                        "shared grass support " + block.palette.shape + "/" + orientation);
                require(world.getBlockState(position).equals(grass), "shared tick retains orientation");
                checked++;
            }
        }
        require(checked > 0, "registered grass pieces were tested");
        world.setBlockToAir(position);
        world.setBlockToAir(position.down());
        System.out.println("BUILDING_PIECES_SHARED_GRASS_PASS states=" + checked);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
