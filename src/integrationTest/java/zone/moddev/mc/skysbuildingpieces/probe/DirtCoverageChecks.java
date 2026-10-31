package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.world.ChunkDataEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyTestScope;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** New coverage revisits old chunks without counting earlier work again. */
public final class DirtCoverageChecks {
    public static void run(ServerWorld world) {
        Chunk testChunk = world.getChunk(90, 90);
        PiecesWorldState totals = PiecesWorldState.get(world);
        long blocks = totals.blocks;
        long chunks = totals.chunks;

        for (int orientation = 0; orientation < 4; orientation++) {
            CompoundNBT raw = NativeLegacyChecks.chunk(LegacyTestScope.DIRT_ID, orientation, "minecraft:dirt");
            CompoundNBT level = raw.getCompound("Level");
            CompoundNBT coverage = new CompoundNBT();

            coverage.putInt("vanilla", 1);
            coverage.putInt("biomesoplenty", 1);
            coverage.putInt("future_module", 7);
            level.put("skysbuildingpieces_coverage", coverage);
            LegacyBridge.prepareChunk(world, raw);

            BlockState actual = LegacyTestScope.staged(level.getList("Sections", 10).getCompound(0), 0);

            NativeLegacyChecks.require(actual.equals(Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, orientation)),
                    "Vertical dirt geometry " + orientation);
            NativeLegacyChecks.require(
                    coverage.getInt("vanilla") == 1 && coverage.getInt("biomesoplenty") == 1
                            && coverage.getInt("future_module") == 7 && coverage.getInt("dirt_vertical_slabs") == 1,
                    "Independent module coverage");
            NativeLegacyChecks.require(
                    !raw.contains("skysbuildingpieces_delta")
                            && raw.getLong("skysbuildingpieces_additional_vanilla_delta") == 1,
                    "New coverage counts only its own conversions");
            NativeLegacyChecks.require(
                    level.getList("TileEntities", 10).isEmpty() && coverage.getList("retained_tiles", 10).getCompound(0)
                            .getString("custom").equals("Retain complete tile data"),
                    "Original material tile retained after conversion");

            MigrationEvents events = new MigrationEvents();

            LegacyBridge.finalizeLegacyChunk(raw);
            events.load(
                    new ChunkDataEvent.Load(testChunk, level, net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));
            NativeLegacyChecks.require(totals.blocks == blocks + orientation + 1 && totals.chunks == chunks,
                    "New dirt coverage must not count the chunk twice");

            CompoundNBT once = raw.copy();

            LegacyBridge.prepareChunk(world, raw);
            NativeLegacyChecks.require(raw.equals(once), "Dirt coverage reload idempotence");
        }

        CompoundNBT item = NativeLegacyChecks.stack("minecraft:dirt", 9);

        item.putString("id", "buildingbricks:dirt_vertical_slab");
        item.putShort("Damage", (short) 30000);

        CompoundNBT custom = item.getCompound("tag").copy();

        LegacyBridge.prepareStack(item);
        NativeLegacyChecks.require(
                item.getString("id").equals("skysbuildingpieces:dirt_vertical_slab") && item.getByte("Count") == 9
                        && item.getCompound("tag").equals(custom),
                "Vertical dirt inventory identity, count and complete custom data");

        CompoundNBT once = item.copy();

        LegacyBridge.prepareStack(item);
        NativeLegacyChecks.require(item.equals(once), "Vertical dirt item reload idempotence");
        System.out.println("BUILDING_PIECES_DIRT_COVERAGE_PASS orientations=4 no_repeat_chunk_count=true");
    }
}
