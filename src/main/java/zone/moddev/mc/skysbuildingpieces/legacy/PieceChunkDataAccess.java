package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;

/** Internal bridge for our migration data outside Minecraft's chunk record fields. */
public interface PieceChunkDataAccess {
    void skysbuildingpieces$setMigrationData(CompoundTag data);
}
