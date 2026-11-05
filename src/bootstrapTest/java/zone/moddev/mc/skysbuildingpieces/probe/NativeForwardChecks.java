package zone.moddev.mc.skysbuildingpieces.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Loads saved source chunks through the normal Forge chunk upgrader. */
final class NativeForwardChecks {
    private static final java.util.Set<net.minecraft.world.level.ChunkPos> WARMUP_CHUNKS = new java.util.LinkedHashSet<>();
    private static final java.util.Set<java.util.UUID> WARMUP_ENTITIES = new java.util.LinkedHashSet<>();
    private static final java.util.Properties LEGACY_DROP_IDENTITIES = new java.util.Properties();

    static void prepare(ServerLevel world) {
        WARMUP_CHUNKS.clear();
        WARMUP_ENTITIES.clear();
        LEGACY_DROP_IDENTITIES.clear();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream("forward-fixture.json"),
                StandardCharsets.UTF_8)) {
            JsonObject fixture = new JsonParser().parse(reader).getAsJsonObject();
            com.google.gson.JsonArray points = new com.google.gson.JsonArray();
            points.addAll(fixture.getAsJsonArray("states"));
            boolean named = fixture.has("kind") && fixture.get("kind").getAsString().equals("named");
            boolean legacy = fixture.has("kind") && fixture.get("kind").getAsString().equals("buildingbricks");
            if (named) {
                NativeBiomeChecks.prepare(world, WARMUP_CHUNKS);
            }
            File identities = new File("legacy-drop-identities.properties");
            if (legacy && identities.isFile()) {
                try (FileInputStream input = new FileInputStream(identities)) {
                    LEGACY_DROP_IDENTITIES.load(input);
                }
                require(fixture.get("archive_sha256").getAsString().equals(
                        LEGACY_DROP_IDENTITIES.getProperty("fixture_sha256")), "Legacy entity receipt belongs to another fixture");
            }
            if (named || legacy) {
                points.addAll(fixture.getAsJsonArray("containers"));
                points.addAll(fixture.getAsJsonArray("entities"));
                for (JsonElement value : fixture.getAsJsonArray("entities")) {
                    JsonObject saved = value.getAsJsonObject();
                    if (!active(saved)) {
                        continue;
                    }
                    if (named) {
                        WARMUP_ENTITIES.add(java.util.UUID.fromString(saved.get("uuid").getAsString()));
                    } else {
                        String key = legacyDropKey(saved);
                        if (!LEGACY_DROP_IDENTITIES.containsKey(key)) {
                            LEGACY_DROP_IDENTITIES.setProperty(key, sourceDropIdentity(world, saved).toString());
                        }
                        WARMUP_ENTITIES.add(java.util.UUID.fromString(LEGACY_DROP_IDENTITIES.getProperty(key)));
                    }
                }
            }
            if (legacy) {
                LEGACY_DROP_IDENTITIES.setProperty("fixture_sha256", fixture.get("archive_sha256").getAsString());
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(identities)) {
                    LEGACY_DROP_IDENTITIES.store(output, "Source fixture entity identities; build-only audit receipt");
                }
            }
            for (JsonElement value : points) {
                JsonObject saved = value.getAsJsonObject();
                if (!active(saved)) {
                    continue;
                }
                net.minecraft.world.level.ChunkPos owner = new net.minecraft.world.level.ChunkPos(position(saved));
                if (!WARMUP_CHUNKS.add(owner)) {
                    continue;
                }
                world.setChunkForced(owner.x, owner.z, true);
                world.getChunk(owner.x, owner.z);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot prepare the disposable forward fixture", failure);
        }
    }

    static boolean ready(ServerLevel world) {
        for (net.minecraft.world.level.ChunkPos chunk : WARMUP_CHUNKS) {
            if (!world.areEntitiesLoaded(chunk.toLong()) || !world.getChunkSource().isPositionTicking(chunk.toLong())) {
                return false;
            }
        }
        for (java.util.UUID id : WARMUP_ENTITIES) {
            if (world.getEntity(id) == null) {
                return false;
            }
        }
        return true;
    }

    static void release(ServerLevel world) {
        for (net.minecraft.world.level.ChunkPos chunk : WARMUP_CHUNKS) {
            world.setChunkForced(chunk.x, chunk.z, false);
        }
        WARMUP_CHUNKS.clear();
        WARMUP_ENTITIES.clear();
        LEGACY_DROP_IDENTITIES.clear();
    }

    static void run(ServerLevel world) throws Exception {
        JsonObject fixture;

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(new File("forward-fixture.json")),
                StandardCharsets.UTF_8)) {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }
        com.google.gson.JsonArray loadPoints = new com.google.gson.JsonArray();

        loadPoints.addAll(fixture.getAsJsonArray("states"));
        if (fixture.has("kind") && fixture.get("kind").getAsString().equals("named")) {
            loadPoints.addAll(fixture.getAsJsonArray("containers"));
            loadPoints.addAll(fixture.getAsJsonArray("entities"));
        }

        // Load the retained halo explicitly; deriving a connection must never make the production block force a
        // neighbouring chunk into memory.
        for (JsonElement entry : loadPoints) {
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

        java.util.Set<net.minecraft.world.level.chunk.LevelChunk> tracked = new java.util.LinkedHashSet<>();

        for (JsonElement entry : loadPoints) {
            JsonObject saved = entry.getAsJsonObject();

            if (!active(saved)) {
                continue;
            }

            int chunkX = saved.get("x").getAsInt() >> 4;
            int chunkZ = saved.get("z").getAsInt() >> 4;

            tracked.add(world.getChunk(chunkX, chunkZ));
            world.setChunkForced(chunkX, chunkZ, true);
        }

        // FULL storage alone does not run native post-processing. A normal ticking ticket must promote the chunk before
        // its geometry is inspected.
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);

        while (pending(tracked) && System.nanoTime() < deadline) {
            if (!world.getChunkSource().pollTask()) {
                Thread.yield();
            }
        }

        require(!pending(tracked), "Native chunk post-processing did not finish");

        for (net.minecraft.world.level.chunk.LevelChunk chunk : tracked) {
            world.setChunkForced(chunk.getPos().x, chunk.getPos().z, false);
        }
        if (fixture.has("kind") && fixture.get("kind").getAsString().equals("named")) {
            checkNativeBlockRenames(world);
            namedPieces(world, fixture);
            return;
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
            net.minecraft.world.level.block.Block registered = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse(id));

            require(registered instanceof PieceBlock, "Permanent palette registration is missing: " + id);

            PieceBlock source = (PieceBlock) registered;
            BlockState expected = Pieces.canonicalState(source.getStateFromMeta(saved.get("meta").getAsInt()));

            // Minecraft adds native equivalents between releases. The source identity remains registered, while
            // recovery may use that equivalent with the same material and occupied half.
            if (expected.getBlock() instanceof PieceBlock) {
                PieceBlock block = (PieceBlock) expected.getBlock();

                expected = block.getActualState(expected, world, position);
            } else {
                expected = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(expected, world, position);
            }

            require(actual.equals(expected), "Saved material, orientation and derived geometry at " + position
                    + ": " + actual + " expected " + expected);
            require(!actual.getValue(SlabBlock.WATERLOGGED), "Legacy pieces begin dry");
            require(world.getBlockEntity(position) == null, "Converted pieces need no material tile");

            if (actual.hasProperty(PieceBlock.CONNECTIONS)) {
                derived++;
            }

            checked++;
        }

        PathChecks.savedPieces(world, true);

        ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(new BlockPos(880, 96, 886));
        int units = 0;

        for (int slot = 0; slot < 20; slot++) {
            ItemStack stack = chest.getItem(slot);

            require(stack.getCount() == slot + 1 && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.hasCustom(stack) && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(stack).contains("path_fixture"),
                    "Saved path inventory count and custom data: " + slot);
            require(stack.getHoverName().getString().equals("Path fixture " + slot),
                    "Saved custom item name: " + slot);
            units += stack.getCount();
        }

        PiecesWorldState state = PiecesWorldState.get(world);

        require(state.blocks == 0 && state.items == 17 && state.chunks == 639,
                "Existing migration counters changed during palette flattening");
        require(state.unresolved.get("buildingbricks:dirt_vertical_slab / missing:unknown / VERTICAL_SLAB") == 2,
                "Existing unresolved report is retained");
        require(new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "data/skysbuildingpieces_legacy_registry.dat")
                .isFile(), "Original registry snapshot survives in its sidecar");
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_NATIVE_FORWARD_PASS states=" + checked + " derived=" + derived
                + " item_units=" + units + " schema=1 counters=unchanged");
    }

    private static void checkNativeBlockRenames(ServerLevel world) throws java.io.IOException {
        java.nio.file.Path path = java.nio.file.Path.of("forward-block-renames.json");
        if (!java.nio.file.Files.isRegularFile(path)) {
            return;
        }
        JsonObject contract = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                .getAsJsonObject();
        require(contract.get("source").getAsString().equals("minecraft:grass")
                        && contract.get("target").getAsString().equals("minecraft:short_grass"),
                "Unexpected block rename in the native fixture");
        java.util.Map<BlockPos, JsonObject> postProcessing = new java.util.HashMap<>();
        if (contract.has("post_processing")) {
            for (JsonElement value : contract.getAsJsonArray("post_processing")) {
                JsonObject expected = value.getAsJsonObject();
                var coordinates = expected.getAsJsonArray("position");
                var position = new BlockPos(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(),
                        coordinates.get(2).getAsInt());
                require(postProcessing.put(position, expected) == null, "Duplicate native post-processing position");
            }
        }
        int checked = 0;
        int processed = 0;
        boolean preHeightChange = contract.has("post_processing");
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (JsonElement value : contract.getAsJsonArray("positions")) {
            var coordinates = value.getAsJsonArray();
            position.set(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt());
            BlockState state = world.getBlockState(position);
            JsonObject expected = postProcessing.remove(position);
            if (!state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)) {
                // After old chunk promotion, vanilla can replace unsupported or flooded plants.
                boolean flooded = state.is(net.minecraft.world.level.block.Blocks.WATER);
                boolean unsupported = !net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState()
                        .canSurvive(world, position);
                boolean matchesRecordedChange = expected != null
                        && blockId(state).equals(expected.get("target").getAsString())
                        && blockId(world.getBlockState(position.below())).equals(expected.get("support").getAsString());
                boolean naturalUpdate = flooded || (state.isAir() && unsupported);
                require(preHeightChange && naturalUpdate
                                && (matchesRecordedChange || expected == null || flooded),
                        "Unexpected vanilla grass post-processing at " + position);
                processed++;
            }
            checked++;
        }
        require(postProcessing.isEmpty() && checked == contract.get("checked_positions").getAsInt(),
                "Incomplete native block rename audit");
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_NATIVE_BLOCK_RENAME_PASS source=minecraft:grass target=minecraft:short_grass positions={}",
                checked);
        if (preHeightChange) {
            org.apache.logging.log4j.LogManager.getLogger().info(
                    "BUILDING_PIECES_NATIVE_OLD_GRASS_POST_PROCESSING retained={} water_or_unsupported={}",
                    checked - processed, processed);
        }
    }

    private static String blockId(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static void namedPieces(ServerLevel world, JsonObject fixture) throws Exception {
        NativeBiomeChecks.verify(world);
        int states = 0;

        for (JsonElement value : fixture.getAsJsonArray("states")) {
            JsonObject saved = value.getAsJsonObject();
            BlockPos position = position(saved);
            net.minecraft.nbt.CompoundTag expected = net.minecraft.nbt.TagParser
                    .parseTag(saved.get("state").getAsString());

            if (expected.getString("Name").equals("minecraft:grass_path")) {
                expected.putString("Name", "minecraft:dirt_path");
            }

            require(net.minecraft.nbt.NbtUtils.writeBlockState(world.getBlockState(position)).equals(expected),
                    "Named state changed during the native upgrade at " + position + ": "
                            + world.getBlockState(position) + " expected " + expected);
            states++;
        }

        int units = 0;

        for (JsonElement value : fixture.getAsJsonArray("containers")) {
            JsonObject saved = value.getAsJsonObject();
            ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(position(saved));

            require(chest != null, "Named-state fixture chest is missing");

            for (JsonElement item : saved.getAsJsonArray("items")) {
                JsonObject entry = item.getAsJsonObject();
                ItemStack expected = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(net.minecraft.nbt.TagParser
                        .parseTag(entry.get("stack").getAsString()));
                ItemStack actual = chest.getItem(entry.get("slot").getAsInt());

                require(ItemStack.matches(expected, actual),
                        "Named-state inventory lost its identity, count or custom data: " + entry);
                units += actual.getCount();
            }
        }

        for (JsonElement value : fixture.getAsJsonArray("entities")) {
            JsonObject saved = value.getAsJsonObject();
            net.minecraft.world.entity.Entity entity = world.getEntity(java.util.UUID
                    .fromString(saved.get("uuid").getAsString()));
            ItemStack expected = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(net.minecraft.nbt.TagParser
                    .parseTag(saved.get("stack").getAsString()));

            require(entity instanceof net.minecraft.world.entity.item.ItemEntity
                    && ItemStack.matches(expected,
                            ((net.minecraft.world.entity.item.ItemEntity) entity).getItem()),
                    "Named-state dropped stack is missing or changed");
            units += expected.getCount();
        }

        if (fixture.has("player")) {
            java.util.UUID id = java.util.UUID.fromString(fixture.get("player").getAsString());
            net.minecraft.server.level.ServerPlayer player = NativeProbeItems.player(
                    world, new com.mojang.authlib.GameProfile(id, "PieceFixture"));
            require(world.getServer().getPlayerList().load(player) != null, "Saved player data is missing");
            ItemStack shovel = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(net.minecraft.nbt.TagParser
                    .parseTag(fixture.get("player_shovel").getAsString()));
            ItemStack nested = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(net.minecraft.nbt.TagParser
                    .parseTag(fixture.get("player_nested").getAsString()));
            require(ItemStack.matches(shovel, player.getInventory().getItem(0)),
                    "Player tool lost its damage, enchantment, name or custom data");
            require(ItemStack.matches(nested, player.getInventory().getItem(1)),
                    "Player nested inventory changed");
            require(ItemStack.matches(nested, player.getEnderChestInventory().getItem(0)),
                    "Ender inventory changed");
            // Save this disconnected fixture player through the native protected path.
            java.lang.reflect.Method save = null;
            for (java.lang.reflect.Method candidate : net.minecraft.server.players.PlayerList.class.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isProtected(candidate.getModifiers())
                        && candidate.getReturnType() == void.class
                        && java.util.Arrays.equals(candidate.getParameterTypes(),
                                new Class<?>[] {net.minecraft.server.level.ServerPlayer.class})) {
                    require(save == null, "The native player-save method is ambiguous");
                    save = candidate;
                }
            }
            require(save != null, "The native player-save method is missing");
            save.setAccessible(true);
            save.invoke(world.getServer().getPlayerList(), player);
        }

        net.minecraft.nbt.CompoundTag expectedState = net.minecraft.nbt.TagParser
                .parseTag(fixture.get("world_state").getAsString());

        require(PiecesWorldState.get(world).save(new net.minecraft.nbt.CompoundTag(), world.registryAccess()).equals(expectedState),
                "Named-state world data, counters or unknown module fields changed");
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_NATIVE_FORWARD_PASS named_states=" + states + " item_units=" + units
                + " waterlogging=true snow=true schema=1 counters=unchanged");
    }

    private static BlockPos position(JsonObject saved) {
        return new BlockPos(saved.get("x").getAsInt(), saved.get("y").getAsInt(), saved.get("z").getAsInt());
    }

    private static void legacyPieces(ServerLevel world, JsonObject fixture) throws Exception {
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
                expected = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(expected, world, position);
            }

            require(actual.equals(expected),
                    "Historical shape/orientation at " + position + ": " + actual + " expected " + expected);
            require(world.getBlockEntity(position) == null, "Converted piece retains an obsolete material tile");
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
            ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(position);

            require(chest != null, "Historical chest survives the native upgrade");

            for (JsonElement value : saved.getAsJsonArray("items")) {
                net.minecraft.nbt.CompoundTag original = net.minecraft.nbt.TagParser
                        .parseTag(value.getAsString());
                int slot = original.getByte("Slot") & 255;
                ItemStack actual = chest.getItem(slot);

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

            net.minecraft.nbt.CompoundTag original = net.minecraft.nbt.TagParser
                    .parseTag(saved.get("item").getAsString());
            double x = saved.get("x").getAsDouble();
            double y = saved.get("y").getAsDouble();
            double z = saved.get("z").getAsDouble();
            java.util.List<net.minecraft.world.entity.item.ItemEntity> found = world.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(x - 8, world.getMinBuildHeight(), z - 8,
                            x + 8, world.getMaxBuildHeight(), z + 8));

            require(found.size() == 1, "Historical dropped stack is missing or duplicated");
            require(found.get(0).getUUID().toString().equals(LEGACY_DROP_IDENTITIES.getProperty(legacyDropKey(saved))),
                    "Historical dropped entity identity changed");
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

        File sidecar = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
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
            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_DELAYED_CHUNKS_UNOPENED count=" + unopened.size());
        }

        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_NATIVE_FORWARD_PASS source=buildingbricks_1.10.2 states=" + states
                + " item_units=" + units + " dropped_entities=" + drops + " schema=1");
    }

    private static boolean active(JsonObject saved) {
        return !saved.has("delayed") || !saved.get("delayed").getAsBoolean()
                || System.getProperty("skysbuildingpieces.bootstrapPhase", "").contains("delayed");
    }

    private static String legacyDropKey(JsonObject saved) {
        return saved.get("x").getAsInt() + "," + saved.get("z").getAsInt();
    }

    private static java.util.UUID sourceDropIdentity(ServerLevel world, JsonObject saved) throws Exception {
        net.minecraft.nbt.CompoundTag chunk = rawChunk(world, saved.get("x").getAsInt() >> 4,
                saved.get("z").getAsInt() >> 4);
        require(chunk.getInt("DataVersion") <= 1343, "Capture legacy entity identities before data fixing");
        net.minecraft.nbt.CompoundTag expected = net.minecraft.nbt.TagParser.parseTag(saved.get("item").getAsString());
        java.util.List<java.util.UUID> matches = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag value : chunk.getCompound("Level").getList("Entities", 10)) {
            net.minecraft.nbt.CompoundTag entity = (net.minecraft.nbt.CompoundTag) value;
            net.minecraft.nbt.ListTag position = entity.getList("Pos", 6);
            if (entity.getCompound("Item").equals(expected) && position.size() == 3
                    && Math.abs(position.getDouble(0) - saved.get("x").getAsDouble()) < 8
                    && Math.abs(position.getDouble(2) - saved.get("z").getAsDouble()) < 8) {
                require(entity.contains("UUIDMost", 4) && entity.contains("UUIDLeast", 4),
                        "Legacy source drop has no stable UUID");
                matches.add(new java.util.UUID(entity.getLong("UUIDMost"), entity.getLong("UUIDLeast")));
            }
        }
        require(matches.size() == 1, "Legacy source drop is missing or ambiguous");
        return matches.get(0);
    }

    private static net.minecraft.nbt.CompoundTag rawChunk(ServerLevel world, int chunkX, int chunkZ) throws Exception {
        File region = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
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
                return net.minecraft.nbt.NbtIo.read(tag);
            }
        }
    }

    private static void recoveredItem(ItemStack actual, net.minecraft.nbt.CompoundTag original) {
        String registry = original.getString("id");
        String material = original.getCompound("tag").getString("material");
        zone.moddev.mc.skysbuildingpieces.catalogue.Shape shape = zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping
                .shape(registry, 0);
        ItemStack expected = Pieces.stack(
                Pieces.state(material, shape, shape == zone.moddev.mc.skysbuildingpieces.catalogue.Shape.SLAB ? 1 : 0),
                1);

        require(actual.getItem() == expected.getItem() && actual.getCount() == (original.getByte("Count") & 255),
                "Historical item identity and count");
        require(zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.hasCustom(actual) && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(actual).equals(original.getCompound("tag")),
                "Historical item material, name and unrelated custom data");
    }

    private static boolean pending(java.util.Set<net.minecraft.world.level.chunk.LevelChunk> chunks) {
        for (net.minecraft.world.level.chunk.LevelChunk chunk : chunks) {
            for (it.unimi.dsi.fastutil.shorts.ShortList positions : chunk.getPostProcessing()) {
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
