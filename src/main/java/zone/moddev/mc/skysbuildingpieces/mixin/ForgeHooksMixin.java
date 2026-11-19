package zone.moddev.mc.skysbuildingpieces.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;

/** Keeps the original registry snapshot available to chunks opened later. */
@Mixin(value = ForgeHooks.class, remap = false)
abstract class ForgeHooksMixin {
    @Inject(method = "readAdditionalLevelSaveData", at = @At("HEAD"))
    private static void skysbuildingpieces$captureLevel(LevelStorageSource.LevelStorageAccess access,
            LevelStorageSource.LevelDirectory directory, CallbackInfo callback) {
        LegacyBridge.captureLegacyLevelData(access, directory);
    }

    @Inject(method = "writeAdditionalLevelSaveData", at = @At("TAIL"))
    private static void skysbuildingpieces$keepSnapshot(WorldData world, CompoundTag level, CallbackInfo callback) {
        LegacyBridge.writeLegacyLevelData(level);
    }
}
