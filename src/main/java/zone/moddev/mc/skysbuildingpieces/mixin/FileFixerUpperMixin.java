package zone.moddev.mc.skysbuildingpieces.mixin;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.util.filefix.FileFixerUpper;
import net.minecraft.util.worldupdate.UpgradeProgress;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;

/** Keeps the old registry data available while Minecraft upgrades world files. */
@Mixin(FileFixerUpper.class)
abstract class FileFixerUpperMixin {
    @ModifyArg(
            method = "writeUpdatedLevelData(Ljava/nio/file/Path;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;readCompressed(Ljava/nio/file/Path;"
                            + "Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;"),
            index = 1,
            require = 1,
            allow = 1)
    private NbtAccounter skysbuildingpieces$readUpgradeLevelData(NbtAccounter quota) {
        // Forge stores registry snapshots in level.dat. The file upgrader
        // rereads that local file with a smaller budget than a normal load.
        return NbtAccounter.create(32L * 1024 * 1024);
    }

    @Inject(
            method = "fix(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
                    + "Lcom/mojang/serialization/Dynamic;"
                    + "Lnet/minecraft/util/worldupdate/UpgradeProgress;)"
                    + "Lcom/mojang/serialization/Dynamic;",
            at = @At("HEAD"))
    private void skysbuildingpieces$captureLegacyLevelData(
            LevelStorageSource.LevelStorageAccess access, Dynamic<?> levelData,
            UpgradeProgress progress, CallbackInfoReturnable<Dynamic<?>> callback) {
        LegacyBridge.captureLegacyLevelData(access, access.getLevelDirectory());
    }
}
