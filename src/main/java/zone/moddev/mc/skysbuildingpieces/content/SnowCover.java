package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Calculated support visuals; no saved grass state or migration data changes. */
public final class SnowCover {
    private SnowCover() {
    }

    public static IBlockState actualState(IBlockState vanilla, IBlockAccess world, BlockPos pos) {
        if (vanilla.getValue(BlockGrass.SNOWY) || pos.getY() >= 255) {
            return vanilla;
        }
        BlockPos abovePos = pos.up();
        if (world instanceof World && !((World) world).isBlockLoaded(abovePos)) {
            return vanilla;
        }
        IBlockState above = world.getBlockState(abovePos);
        Block block = above.getBlock();
        // Full blocks do not expose their support. This also avoids following another grass flag.
        if (above.isFullCube() || above.getMaterial().isLiquid() || block.hasTileEntity(above)) {
            return vanilla;
        }
        boolean physicalSnow = isSnowPiece(above);
        if (block instanceof PieceBlock) {
            PieceBlock piece = (PieceBlock) block;
            if (piece.palette.shape == Shape.WALL || piece.palette.shape == Shape.PANE
                    || (piece.mask(above) & 15) == 0) {
                return vanilla;
            }
        } else {
            AxisAlignedBB bounds = block.getBoundingBox(above, world, abovePos);
            if (bounds == null || bounds.minY != 0 || bounds.maxY <= 0 || bounds.maxY >= 1) {
                return vanilla;
            }
        }
        boolean calculatedSnow = above.getPropertyKeys().contains(BlockGrass.SNOWY)
                && GrassLifecycle.hasSnowAppearance(world, abovePos);
        return physicalSnow || calculatedSnow ? vanilla.withProperty(BlockGrass.SNOWY, true) : vanilla;
    }

    static boolean isSnowPiece(IBlockState state) {
        Block block = state.getBlock();
        return block == Blocks.SNOW || block == Blocks.SNOW_LAYER
                || block instanceof PieceBlock && ((PieceBlock) block).definition(state).type.equals("snow");
    }
}
