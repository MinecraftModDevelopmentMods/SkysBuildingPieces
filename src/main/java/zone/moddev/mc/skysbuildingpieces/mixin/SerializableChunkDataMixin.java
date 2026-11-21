package zone.moddev.mc.skysbuildingpieces.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;
import zone.moddev.mc.skysbuildingpieces.legacy.PieceChunkDataAccess;

@Mixin(SerializableChunkData.class)
abstract class SerializableChunkDataMixin implements PieceChunkDataAccess {
    @Unique
    private CompoundTag skysbuildingpieces$migrationData;

    @Override
    public void skysbuildingpieces$setMigrationData(CompoundTag data) {
        skysbuildingpieces$migrationData = data;
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private static void skysbuildingpieces$readMarkers(LevelHeightAccessor height, PalettedContainerFactory palettes,
            CompoundTag root, CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData data = callback.getReturnValue();
        if (data != null) {
            CompoundTag markers = MigrationEvents.copyMigrationData(root);
            if (!markers.isEmpty()) {
                ((PieceChunkDataAccess) (Object) data).skysbuildingpieces$setMigrationData(markers);
            }
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void skysbuildingpieces$loadMarkers(ServerLevel world, PoiManager poi, RegionStorageInfo storage,
            ChunkPos position, CallbackInfoReturnable<ProtoChunk> callback) {
        ProtoChunk chunk = callback.getReturnValue();
        ChunkAccess loaded = chunk instanceof ImposterProtoChunk wrapper ? wrapper.getWrapped() : chunk;
        MigrationEvents.loadSerializedChunk(world, loaded, skysbuildingpieces$migrationData);
    }

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void skysbuildingpieces$copyMarkers(ServerLevel world, ChunkAccess chunk,
            CallbackInfoReturnable<SerializableChunkData> callback) {
        CompoundTag markers = MigrationEvents.copyCoverageForSave(chunk);
        if (markers != null) {
            ((PieceChunkDataAccess) (Object) callback.getReturnValue()).skysbuildingpieces$setMigrationData(markers);
        }
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void skysbuildingpieces$writeMarkers(CallbackInfoReturnable<CompoundTag> callback) {
        if (skysbuildingpieces$migrationData != null) {
            callback.getReturnValue().merge(skysbuildingpieces$migrationData);
        }
    }
}
