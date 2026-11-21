package zone.moddev.mc.skysbuildingpieces.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;

/** Preserves legacy items and saved data across vanilla's upgrade. */
@Mixin(DataFixTypes.class)
abstract class DataFixTypesMixin {
    private static final String UPDATE =
            "update(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;";
    private static final int MINECRAFT_1_20_1 = 3465;

    @Inject(method = UPDATE, at = @At("HEAD"))
    private void skysbuildingpieces$prepareInventory(DataFixer fixer, CompoundTag input,
            int sourceVersion, int targetVersion, CallbackInfoReturnable<CompoundTag> callback) {
        LegacyBridge.prepareNbt(input);
    }

    @Inject(method = UPDATE, at = @At("RETURN"))
    private void skysbuildingpieces$finishInventory(DataFixer fixer, CompoundTag input,
            int sourceVersion, int targetVersion, CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag fixed = callback.getReturnValue();
        LegacyBridge.finalizeNbt(fixed, targetVersion);

        // 1.20.1 had no salt. Vanilla wraps its existing sequences but the new codec
        // rejects the file without this field, resetting every saved sequence.
        if ((Object) this == DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
                && sourceVersion == MINECRAFT_1_20_1 && targetVersion > sourceVersion) {
            fixed.getCompound("data").ifPresent(data -> {
                if (data.getCompound("sequences").isPresent() && !data.contains("salt")) {
                    data.putInt("salt", 0);
                }
            });
        }
    }
}
