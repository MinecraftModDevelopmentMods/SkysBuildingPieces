package zone.moddev.mc.skysbuildingpieces.compat;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;

/** Internal native-state bridge; ordinary blocks retain their cached hardness. */
public final class NativeStateHooks {
    private NativeStateHooks() {
    }

    public static float destroySpeed(float original, AbstractBlock.AbstractBlockState state,
            IBlockReader world, BlockPos position) {
        Block block = state.getBlock();
        if (block instanceof PieceBlock && state instanceof BlockState) {
            return ((PieceBlock) block).materialDestroySpeed((BlockState) state, world, position);
        }
        return original;
    }
}
