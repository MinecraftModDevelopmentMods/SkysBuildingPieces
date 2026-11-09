package zone.moddev.mc.skysbuildingpieces.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysbuildingpieces.compat.NativeStateHooks;

/** Each material in a shared palette keeps its original hardness. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class PieceHardnessMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void skysbuildingpieces$materialHardness(BlockGetter world, BlockPos position,
            CallbackInfoReturnable<Float> callback) {
        float original = callback.getReturnValueF();
        float hardness = NativeStateHooks.destroySpeed(original, (BlockBehaviour.BlockStateBase) (Object) this,
                world, position);
        if (hardness != original) {
            callback.setReturnValue(hardness);
        }
    }
}
