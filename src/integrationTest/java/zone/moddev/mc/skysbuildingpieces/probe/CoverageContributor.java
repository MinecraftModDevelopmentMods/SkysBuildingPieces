package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

/** Exercises independent add-on coverage through the native chunk snapshot. */
public final class CoverageContributor {
    private CoverageContributor() {
    }

    public static void verify(ServerLevel world) {
        LevelChunk chunk = world.getChunk(72, 72);

        for (boolean wrapped : new boolean[] {true, false}) {
            CompoundTag root = new CompoundTag();
            CompoundTag level = root;
            if (wrapped) {
                level = new CompoundTag();
                root.put("Level", level);
            }
            CompoundTag coverage = new CompoundTag();
            coverage.putInt("vanilla", 1);
            level.put("skysbuildingpieces_coverage", coverage);

            if (!visit(coverage)) {
                throw new AssertionError("Core coverage must not skip a later add-on");
            }
            MigrationEvents.loadSerializedChunk(world, chunk, MigrationEvents.copyMigrationData(root));
            CompoundTag saved = SerializableChunkData.copyOf(world, chunk).write();
            CompoundTag markers = saved.getCompoundOrEmpty("skysbuildingpieces_coverage");
            if (markers.getIntOr("vanilla", 0) != 1 || markers.getIntOr("later_addon", 0) != 1) {
                throw new AssertionError("Both coverage markers must survive the native chunk save");
            }
            MigrationEvents.loadSerializedChunk(world, chunk, MigrationEvents.copyMigrationData(saved));
            if (visit(SerializableChunkData.copyOf(world, chunk).write()
                    .getCompoundOrEmpty("skysbuildingpieces_coverage"))) {
                throw new AssertionError("Reload repeated an add-on's completed coverage");
            }
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_CONTRIBUTOR_PASS independent load/save/reload schemas=2");
    }

    private static boolean visit(CompoundTag coverage) {
        if (coverage.getIntOr("later_addon", 0) >= 1) {
            return false;
        }
        coverage.putInt("later_addon", 1);
        return true;
    }
}
