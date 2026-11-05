package zone.moddev.mc.skysbuildingpieces.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysbuildingpieces.content.NativeSlabPlacement;

/** Lets a supported native slab use the same placement rules as a Sky slab. */
@Mixin(BlockItem.class)
abstract class NativeSlabPlacementMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void skysbuildingpieces$placeVerticalSlab(UseOnContext context,
            CallbackInfoReturnable<InteractionResult> callback) {
        InteractionResult result = NativeSlabPlacement.place(context);
        if (result != null) {
            callback.setReturnValue(result);
        }
    }
}
