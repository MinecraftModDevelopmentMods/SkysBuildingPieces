package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ChunkDataEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.probe.legacy.LegacyTestScope;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** New coverage revisits old chunks without counting earlier work again. */
public final class DirtCoverageChecks {
    public static void run(ServerLevel world) {
        LevelChunk testChunk = world.getChunk(90, 90);
        PiecesWorldState totals = PiecesWorldState.get(world);
        long blocks = totals.blocks;
        long chunks = totals.chunks;

        for (int orientation = 0; orientation < 4; orientation++) {
            CompoundTag raw = NativeLegacyChecks.chunk(LegacyTestScope.DIRT_ID, orientation, "minecraft:dirt");
            CompoundTag level = raw.getCompoundOrEmpty("Level");
            CompoundTag coverage = new CompoundTag();

            coverage.putInt("vanilla", 1);
            coverage.putInt("biomesoplenty", 1);
            coverage.putInt("future_module", 7);
            level.put("skysbuildingpieces_coverage", coverage);
            LegacyBridge.prepareChunk(world, raw);

            BlockState actual = LegacyTestScope.staged(level.getListOrEmpty("Sections").getCompoundOrEmpty(0), 0);

            NativeLegacyChecks.require(actual.equals(Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, orientation)),
                    "Vertical dirt geometry " + orientation);
            NativeLegacyChecks.require(
                    coverage.getIntOr("vanilla", 0) == 1 && coverage.getIntOr("biomesoplenty", 0) == 1
                            && coverage.getIntOr("future_module", 0) == 7 && coverage.getIntOr("dirt_vertical_slabs", 0) == 1,
                    "Independent module coverage");
            NativeLegacyChecks.require(
                    !raw.contains("skysbuildingpieces_delta")
                            && raw.getLongOr("skysbuildingpieces_additional_vanilla_delta", 0L) == 1,
                    "New coverage counts only its own conversions");
            NativeLegacyChecks.require(
                    level.getListOrEmpty("TileEntities").isEmpty() && coverage.getListOrEmpty("retained_tiles").getCompoundOrEmpty(0)
                            .getStringOr("custom", "").equals("Retain complete tile data"),
                    "Original material tile retained after conversion");

            LegacyBridge.finalizeLegacyChunk(raw);
            MigrationEvents.loadSerializedChunk(world, testChunk, MigrationEvents.copyMigrationData(level));
            NativeLegacyChecks.require(totals.blocks == blocks + orientation + 1 && totals.chunks == chunks,
                    "New dirt coverage must not count the chunk twice");

            CompoundTag once = raw.copy();

            LegacyBridge.prepareChunk(world, raw);
            NativeLegacyChecks.require(raw.equals(once), "Dirt coverage reload idempotence");
        }

        CompoundTag item = NativeLegacyChecks.stack("minecraft:dirt", 9);

        item.putString("id", "buildingbricks:dirt_vertical_slab");
        item.putShort("Damage", (short) 30000);

        CompoundTag custom = item.getCompoundOrEmpty("tag").copy();

        LegacyBridge.prepareStack(item);
        NativeLegacyChecks.require(
                item.getStringOr("id", "").equals("skysbuildingpieces:dirt_vertical_slab") && item.getByteOr("Count", (byte) 0) == 9
                        && item.getCompoundOrEmpty("tag").equals(custom),
                "Vertical dirt inventory identity, count and complete custom data");

        CompoundTag once = item.copy();

        LegacyBridge.prepareStack(item);
        NativeLegacyChecks.require(item.equals(once), "Vertical dirt item reload idempotence");
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_DIRT_COVERAGE_PASS orientations=4 no_repeat_chunk_count=true");
    }
}
