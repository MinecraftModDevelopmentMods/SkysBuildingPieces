package zone.moddev.mc.skysbuildingpieces.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;

/** Wraps vanilla data fixing so recovered piece states survive conversion. */
@Mixin(SimpleRegionStorage.class)
abstract class SimpleRegionStorageMixin {
    @Shadow
    @Final
    private DataFixTypes dataFixType;

    @Inject(
            method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("HEAD"))
    private void skysbuildingpieces$prepareLegacyChunk(CompoundTag chunk,
            int fallbackDataVersion, @Nullable CompoundTag context,
            CallbackInfoReturnable<CompoundTag> callback) {
        if (dataFixType == DataFixTypes.CHUNK) {
            LegacyBridge.prepareLegacyChunk(chunk);
        }
    }

    @Inject(
            method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"))
    private void skysbuildingpieces$finalizeLegacyChunk(CompoundTag chunk,
            int fallbackDataVersion, @Nullable CompoundTag context,
            CallbackInfoReturnable<CompoundTag> callback) {
        if (dataFixType == DataFixTypes.CHUNK) {
            LegacyBridge.finalizeLegacyChunk(callback.getReturnValue());
        }
    }
}
