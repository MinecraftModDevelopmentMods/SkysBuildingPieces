package zone.moddev.mc.skysbuildingpieces.compat;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;

/** Internal native-state bridge; ordinary blocks retain their cached hardness. */
public final class NativeStateHooks {
    private NativeStateHooks() {
    }

    public static float destroySpeed(float original, BlockBehaviour.BlockStateBase state,
            BlockGetter world, BlockPos position) {
        Block block = state.getBlock();
        if (block instanceof PieceBlock && state instanceof BlockState) {
            return ((PieceBlock) block).materialDestroySpeed((BlockState) state, world, position);
        }
        return original;
    }
}
