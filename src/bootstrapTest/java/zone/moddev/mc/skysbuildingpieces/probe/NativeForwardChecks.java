package zone.moddev.mc.skysbuildingpieces.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Loads saved source chunks through the normal Forge chunk upgrader. */
final class NativeForwardChecks {
    static void run(ServerWorld world) throws Exception {
        JsonObject fixture;

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(new File("forward-fixture.json")),
                StandardCharsets.UTF_8)) {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }
        // Load the retained halo explicitly; deriving a connection must never make the production block force a
        // neighbouring chunk into memory.
        for (JsonElement entry : fixture.getAsJsonArray("states")) {
            JsonObject state = entry.getAsJsonObject();

            if (!active(state)) {
                continue;
            }

            int chunkX = state.get("x").getAsInt() >> 4;
            int chunkZ = state.get("z").getAsInt() >> 4;

            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    world.getChunk(chunkX + offsetX, chunkZ + offsetZ);
                }
            }
        }

        java.util.Set<net.minecraft.world.chunk.Chunk> tracked = new java.util.LinkedHashSet<>();

        for (JsonElement entry : fixture.getAsJsonArray("states")) {
            JsonObject saved = entry.getAsJsonObject();

            if (!active(saved)) {
                continue;
            }

            int chunkX = saved.get("x").getAsInt() >> 4;
            int chunkZ = saved.get("z").getAsInt() >> 4;

            tracked.add(world.getChunk(chunkX, chunkZ));
            world.forceChunk(chunkX, chunkZ, true);
        }

        // FULL storage alone does not run native post-processing. A normal ticking ticket must promote the chunk before
        // its geometry is inspected.
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);

        while (pending(tracked) && System.nanoTime() < deadline) {
            if (!world.getChunkProvider().func_217234_d()) {
                Thread.yield();
            }
        }

        require(!pending(tracked), "Native chunk post-processing did not finish");

        for (net.minecraft.world.chunk.Chunk chunk : tracked) {
            world.forceChunk(chunk.getPos().x, chunk.getPos().z, false);
        }
        if (fixture.has("kind") && fixture.get("kind").getAsString().equals("buildingbricks")) {
            legacyPieces(world, fixture);
            return;
        }

        int checked = 0;
        int derived = 0;

        for (JsonElement entry : fixture.getAsJsonArray("states")) {
            JsonObject saved = entry.getAsJsonObject();
            BlockPos position = new BlockPos(saved.get("x").getAsInt(), saved.get("y").getAsInt(),
                    saved.get("z").getAsInt());
            String id = saved.get("id").getAsString();
            BlockState actual = world.getBlockState(position);

            require(actual.getBlock() instanceof PieceBlock
                    && actual.getBlock().getRegistryName().toString().equals(id),
                    "Saved palette identity at " + position + ": " + actual + " expected " + id);

            PieceBlock block = (PieceBlock) actual.getBlock();

            require(block.getMetaFromState(actual) == saved.get("meta").getAsInt(),
                    "Saved material and orientation at " + position);
            require(!actual.get(SlabBlock.WATERLOGGED), "Legacy pieces begin dry");
            require(world.getTileEntity(position) == null, "Converted pieces need no material tile");

            BlockState connected = block.getActualState(actual, world, position);

            require(actual.equals(connected),
                    "Saved derived geometry at " + position + ": " + actual + " expected " + connected);

            if (actual.has(PieceBlock.CONNECTIONS)) {
                derived++;
            }

            checked++;
        }

        PathChecks.savedPieces(world, true);

        ChestTileEntity chest = (ChestTileEntity) world.getTileEntity(new BlockPos(880, 96, 886));
        int units = 0;

        for (int slot = 0; slot < 20; slot++) {
            ItemStack stack = chest.getStackInSlot(slot);

            require(stack.getCount() == slot + 1 && stack.hasTag() && stack.getTag().contains("path_fixture"),
                    "Saved path inventory count and custom data: " + slot);
            require(stack.getDisplayName().getString().equals("Path fixture " + slot),
                    "Saved custom item name: " + slot);
            units += stack.getCount();
        }

        PiecesWorldState state = PiecesWorldState.get(world);

        require(state.blocks == 0 && state.items == 17 && state.chunks == 639,
                "Existing migration counters changed during palette flattening");
        require(state.unresolved.get("buildingbricks:dirt_vertical_slab / missing:unknown / VERTICAL_SLAB") == 2,
                "Existing unresolved report is retained");
        require(new File(world.getSaveHandler().getWorldDirectory(), "data/skysbuildingpieces_legacy_registry.dat")
                .isFile(), "Original registry snapshot survives in its sidecar");
        System.out.println("BUILDING_PIECES_NATIVE_FORWARD_PASS states=" + checked + " derived=" + derived
                + " item_units=" + units + " schema=1 counters=unchanged");
    }

    private static void legacyPieces(ServerWorld world, JsonObject fixture) throws Exception {
        int states = 0;
        java.util.Set<String> chunks = new java.util.HashSet<>();

        for (JsonElement entry : fixture.getAsJsonArray("states")) {
            JsonObject saved = entry.getAsJsonObject();

            if (!active(saved)) {
                continue;
            }

            BlockPos position = new BlockPos(saved.get("x").getAsInt(), saved.get("y").getAsInt(),
                    saved.get("z").getAsInt());

            chunks.add((position.getX() >> 4) + "," + (position.getZ() >> 4));

            String registry = saved.get("id").getAsString();
            int metadata = saved.get("meta").getAsInt();
            zone.moddev.mc.skysbuildingpieces.catalogue.Shape shape = zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping
                    .shape(registry, metadata);
            int orientation = zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping.orientation(shape, metadata);
            BlockState expected = Pieces.state(saved.get("material").getAsString(), shape, orientation);
            BlockState actual = world.getBlockState(position);

            if (expected.getBlock() instanceof PieceBlock) {
                expected = ((PieceBlock) expected.getBlock()).getActualState(expected, world, position);
            } else {
                expected = net.minecraft.block.Block.getValidBlockForPosition(expected, world, position);
            }

            require(actual.equals(expected),
                    "Historical shape/orientation at " + position + ": " + actual + " expected " + expected);
            require(world.getTileEntity(position) == null, "Converted piece retains an obsolete material tile");
            states++;
        }

        int units = 0;
        int expectedUnits = 0;

        for (JsonElement entry : fixture.getAsJsonArray("containers")) {
            JsonObject saved = entry.getAsJsonObject();

            if (!active(saved)) {
                continue;
            }

            BlockPos position = new BlockPos(saved.get("x").getAsInt(), saved.get("y").getAsInt(),
                    saved.get("z").getAsInt());
            ChestTileEntity chest = (ChestTileEntity) world.getTileEntity(position);

            require(chest != null, "Historical chest survives the native upgrade");

            for (JsonElement value : saved.getAsJsonArray("items")) {
                net.minecraft.nbt.CompoundNBT original = net.minecraft.nbt.JsonToNBT
                        .getTagFromJson(value.getAsString());
                int slot = original.getByte("Slot") & 255;
                ItemStack actual = chest.getStackInSlot(slot);

                recoveredItem(actual, original);
                units += actual.getCount();
                expectedUnits += original.getByte("Count") & 255;
            }
        }

        int drops = 0;
        int expectedDrops = 0;

        for (JsonElement value : fixture.getAsJsonArray("entities")) {
            JsonObject saved = value.getAsJsonObject();

            if (!active(saved)) {
                continue;
            }

            net.minecraft.nbt.CompoundNBT original = net.minecraft.nbt.JsonToNBT
                    .getTagFromJson(saved.get("item").getAsString());
            double x = saved.get("x").getAsDouble();
            double y = saved.get("y").getAsDouble();
            double z = saved.get("z").getAsDouble();
            java.util.List<net.minecraft.entity.item.ItemEntity> found = world.getEntitiesWithinAABB(
                    net.minecraft.entity.item.ItemEntity.class,
                    new net.minecraft.util.math.AxisAlignedBB(x - 8, y - 8, z - 8, x + 8, y + 8, z + 8));

            require(found.size() == 1, "Historical dropped stack is missing or duplicated");
            recoveredItem(found.get(0).getItem(), original);
            units += found.get(0).getItem().getCount();
            expectedUnits += original.getByte("Count") & 255;
            drops++;
            expectedDrops++;
        }

        zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge.report(world);

        PiecesWorldState totals = PiecesWorldState.get(world);

        require(states > 0 && units == expectedUnits && drops == expectedDrops,
                "Historical block/container/entity totals");
        require(totals.blocks == states && totals.items == units && totals.chunks == chunks.size(),
                "Historical migration counters: " + totals.blocks + "/" + totals.items + "/" + totals.chunks);

        File sidecar = new File(world.getSaveHandler().getWorldDirectory(),
                "data/skysbuildingpieces_legacy_registry.dat");

        require(sidecar.isFile(), "Original numeric registry survives in the Sky sidecar");

        java.util.Set<String> unopened = new java.util.HashSet<>();

        for (JsonElement entry : fixture.getAsJsonArray("states")) {
            JsonObject saved = entry.getAsJsonObject();

            if (!active(saved)) {
                int chunkX = saved.get("x").getAsInt() >> 4;
                int chunkZ = saved.get("z").getAsInt() >> 4;

                if (unopened.add(chunkX + "," + chunkZ)) {
                    require(rawChunk(world, chunkX, chunkZ).getInt("DataVersion") == 512,
                            "The delayed legacy chunk must remain unopened during the first save");
                }
            }
        }
        if (!unopened.isEmpty()) {
            System.out.println("BUILDING_PIECES_DELAYED_CHUNKS_UNOPENED count=" + unopened.size());
        }

        System.out.println("BUILDING_PIECES_NATIVE_FORWARD_PASS source=buildingbricks_1.10.2 states=" + states
                + " item_units=" + units + " dropped_entities=" + drops + " schema=1");
    }

    private static boolean active(JsonObject saved) {
        return !saved.has("delayed") || !saved.get("delayed").getAsBoolean()
                || System.getProperty("skysbuildingpieces.bootstrapPhase", "").contains("delayed");
    }

    private static net.minecraft.nbt.CompoundNBT rawChunk(ServerWorld world, int chunkX, int chunkZ) throws Exception {
        File region = new File(world.getSaveHandler().getWorldDirectory(),
                "region/r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");

        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(region, "r")) {
            input.seek(((chunkX & 31) + (chunkZ & 31) * 32) * 4L);

            int location = input.readInt();

            require(location != 0, "The delayed fixture chunk is missing");
            input.seek((location >>> 8) * 4096L);

            int length = input.readInt();
            int compression = input.readUnsignedByte();

            require(length > 1 && length <= (location & 255) * 4096 - 4, "Invalid delayed fixture extent");

            byte[] data = new byte[length - 1];

            input.readFully(data);

            java.io.InputStream bytes = new java.io.ByteArrayInputStream(data);

            bytes = compression == 2 ? new java.util.zip.InflaterInputStream(bytes)
                    : compression == 1 ? new java.util.zip.GZIPInputStream(bytes) : bytes;

            try (java.io.DataInputStream tag = new java.io.DataInputStream(bytes)) {
                return net.minecraft.nbt.CompressedStreamTools.read(tag);
            }
        }
    }

    private static void recoveredItem(ItemStack actual, net.minecraft.nbt.CompoundNBT original) {
        String registry = original.getString("id");
        String material = original.getCompound("tag").getString("material");
        zone.moddev.mc.skysbuildingpieces.catalogue.Shape shape = zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping
                .shape(registry, 0);
        ItemStack expected = Pieces.stack(
                Pieces.state(material, shape, shape == zone.moddev.mc.skysbuildingpieces.catalogue.Shape.SLAB ? 1 : 0),
                1);

        require(actual.getItem() == expected.getItem() && actual.getCount() == (original.getByte("Count") & 255),
                "Historical item identity and count");
        require(actual.hasTag() && actual.getTag().equals(original.getCompound("tag")),
                "Historical item material, name and unrelated custom data");
    }

    private static boolean pending(java.util.Set<net.minecraft.world.chunk.Chunk> chunks) {
        for (net.minecraft.world.chunk.Chunk chunk : chunks) {
            for (it.unimi.dsi.fastutil.shorts.ShortList positions : chunk.getPackedPositions()) {
                if (positions != null && !positions.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
