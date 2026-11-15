package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Uses vanilla's snow-placement rule rather than checking our own support flag alone. */
final class SnowSettlementChecks {
    static void run(WorldServer world) {
        BlockPos position = new BlockPos(1480, 220, 1480);
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                world.getChunk((position.getX() >> 4) + chunkX, (position.getZ() >> 4) + chunkZ);
            }
        }
        world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockToAir(position.up());
        world.setBlockState(position, Pieces.state("minecraft:grass", Shape.STEP, 4), 2);
        require(!Blocks.SNOW_LAYER.canPlaceBlockAt(world, position.up()),
                "snow must not hover over a bottom grass step");
        int cases = 0;
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            Block nativeMethods = block;
            for (int orientation = 0; orientation < block.palette.shape.states; orientation++) {
                IBlockState state = nativeMethods.getStateFromMeta(orientation);
                world.setBlockState(position, state, 2);
                boolean completeTop = block.palette.shape != Shape.WALL && block.palette.shape != Shape.PANE
                        && !block.palette.group.equals("path") && Geometry.completeTop(block.mask(state));
                require(Blocks.SNOW_LAYER.canPlaceBlockAt(world, position.up()) == completeTop,
                        "snow needs a full-height supporting top: " + state);
                for (EnumFacing face : EnumFacing.values()) {
                    BlockFaceShape expected = block.palette.shape == Shape.WALL
                            ? (face.getAxis() == EnumFacing.Axis.Y ? BlockFaceShape.CENTER_BIG
                                    : BlockFaceShape.MIDDLE_POLE_THICK)
                            : block.palette.shape == Shape.PANE
                                    ? (face.getAxis() == EnumFacing.Axis.Y ? BlockFaceShape.CENTER_SMALL
                                            : BlockFaceShape.MIDDLE_POLE_THIN)
                                    : nativeMethods.isSideSolid(state, world, position, face) ? BlockFaceShape.SOLID
                                            : BlockFaceShape.UNDEFINED;
                    require(state.getBlockFaceShape(world, position, face) == expected,
                            "face follows the occupied geometry: " + state + "/" + face);
                }
                if (block.palette.shape == Shape.WALL) {
                    require(Blocks.TORCH.canPlaceBlockAt(world, position.up()), "wall torch support");
                }
                for (int layers : new int[] { 1, 8 }) {
                    IBlockState snow = Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, layers);
                    world.setBlockState(position.up(), snow, 2);
                    nativeMethods.neighborChanged(state, world, position, Blocks.SNOW_LAYER, position.up());
                    require((world.getBlockState(position.up()).getBlock() == Blocks.SNOW_LAYER) == completeTop,
                            "a neighbour update removes only unsupported saved snow: " + state);
                    require(world.getBlockState(position).equals(state), "snow repair keeps the piece metadata");
                    world.setBlockToAir(position.up());
                }
                if (nativeMethods.getTickRandomly()) {
                    world.setBlockState(position.up(), Blocks.SNOW_LAYER.getDefaultState(), 2);
                    nativeMethods.updateTick(world, position, state, new Random(4815162342L));
                    require((world.getBlockState(position.up()).getBlock() == Blocks.SNOW_LAYER) == completeTop,
                            "ordinary soil ticks remove old floating snow: " + state);
                    world.setBlockToAir(position.up());
                }
                cases++;
            }
        }
        world.setBlockState(position, Blocks.STONE.getDefaultState(), 2);
        require(Blocks.SNOW_LAYER.canPlaceBlockAt(world, position.up()), "vanilla full-block control");
        world.setBlockToAir(position);
        world.setBlockToAir(position.down());
        System.out.println("BUILDING_PIECES_SNOW_SETTLEMENT_PASS states=" + cases
                + " native_rule=true full_tops=true saved_snow=true walls=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private SnowSettlementChecks() {
    }
}
