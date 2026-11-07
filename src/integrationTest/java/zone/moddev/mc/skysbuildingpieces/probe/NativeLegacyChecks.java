package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;
import zone.moddev.mc.skysbuildingpieces.probe.legacy.LegacyTestScope;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Exercises raw recovery before the target's native data fixer reads a chunk. */
public final class NativeLegacyChecks {
    private NativeLegacyChecks() {
    }

    public static void run(ServerLevel world) throws Exception {
        zone.moddev.mc.skysbuildingpieces.probe.legacy.NativeStagingChecks.run(world);
        flattenedPieces();

        boolean forced = SkysBuildingPieces.forceReplace;
        boolean installed = ModList.get().isLoaded("buildingbricks");

        try (LegacyTestScope source = new LegacyTestScope(world)) {
            SkysBuildingPieces.forceReplace = true;

            for (int code = 0; code < 16; code++) {
                CompoundTag raw = chunk(LegacyTestScope.STEP_ID, code, "minecraft:stone");

                LegacyBridge.prepareLegacyChunk(raw);

                CompoundTag level = raw.getCompound("Level");
                BlockState actual = LegacyTestScope.staged(level.getList("Sections", 10).getCompound(0), 0);
                Shape shape = LegacyMapping.shape("buildingbricks:rock_step", code);
                int orientation = LegacyMapping.orientation(shape, code);

                require(actual != null && actual.equals(Pieces.state("minecraft:stone", shape, orientation)),
                        "Saved step code " + code + " resolved to " + actual);
                require(((PieceBlock) actual.getBlock()).mask(actual) == Geometry.mask(shape, orientation),
                        "Saved step geometry " + code);
                require(level.getList("TileEntities", 10).isEmpty(), "Successful conversion removes its old tile");
                require(level.getCompound("skysbuildingpieces_coverage").getList("retained_tiles", 10).getCompound(0)
                        .getString("custom").equals("Retain complete tile data"), "Retained tile data");

                CompoundTag once = raw.copy();

                LegacyBridge.prepareLegacyChunk(raw);
                require(once.equals(raw), "Raw chunk recovery is idempotent");

                CompoundTag fixed = DataFixTypes.CHUNK.update(world.getServer().getFixerUpper(), raw.copy(), 1343, 1976);

                require(contains(fixed.getCompound("Level"), actual), "Native flattening lost step code " + code);
                require(!fixed.getCompound("Level").getList("Sections", 10).getCompound(0).contains("Blocks"),
                        "The native data fixer must replace numeric arrays");
                LegacyBridge.finalizeLegacyChunk(fixed);
                require(fixed.getCompound("Level").getString("Status").equals("full"), "Recovered chunk remains full");
            }

            DirtCoverageChecks.run(world);
            storedInventories(world);

            if (!installed) {
                unknownContentStops();
            }
            if (installed) {
                SkysBuildingPieces.forceReplace = false;

                CompoundTag raw = chunk(LegacyTestScope.STEP_ID, 11, "minecraft:stone");
                CompoundTag before = raw.copy();

                LegacyBridge.prepareLegacyChunk(raw);
                require(raw.equals(before), "Installed/off must leave raw blocks, items and coverage unchanged");
                SkysBuildingPieces.forceReplace = true;
            }

            // Already flattened saves must never enter numeric recovery.
            CompoundTag modern = new CompoundTag();

            modern.putInt("DataVersion", 1519);
            modern.put("Level", new CompoundTag());

            CompoundTag before = modern.copy();

            LegacyBridge.prepareLegacyChunk(modern);
            require(modern.equals(before), "Named-state chunks bypass the numeric bridge");
            CoverageContributor.verify(world);
        } finally {
            SkysBuildingPieces.forceReplace = forced;
        }

        System.out
                .println("BUILDING_PIECES_NATIVE_LEGACY_PASS step_codes=16 native_datafix=true installed=" + installed);
    }

    private static void flattenedPieces() {
        int checked = 0;
        for (PieceBlock carrier : Pieces.BLOCKS.values()) {
            for (String material : carrier.palette.materials) {
                for (int orientation = 0; orientation < carrier.palette.shape.states; orientation++) {
                    BlockState old = carrier.getStateFromMeta(carrier.palette.meta(material, orientation))
                            .setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, true);
                    BlockState target = Pieces.canonicalState(old);
                    if (!(target.getBlock() instanceof PieceBlock piece)) {
                        continue;
                    }
                    require(!piece.isLegacyPalette() && piece.palette.materials.size() == 1
                            && piece.getStateDefinition().getProperty("meta") == null,
                            "A playable block still stores material metadata: " + target);
                    require(piece.mask(target) == carrier.mask(old)
                            && target.getValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED),
                            "Flattening changed geometry or water: " + old + " -> " + target);
                    CompoundTag sample = new CompoundTag();
                    ListTag palette = new ListTag();
                    palette.add(NbtUtils.writeBlockState(old));
                    sample.put("palette", palette);
                    LegacyBridge.finalizeNbt(sample);
                    require(palette.getCompound(0).equals(NbtUtils.writeBlockState(target)),
                            "Named palette recovery failed: " + old);
                    CompoundTag first = sample.copy();
                    LegacyBridge.finalizeNbt(sample);
                    require(first.equals(sample), "Named palette recovery is not idempotent");
                    checked++;
                }
            }
        }
        require(checked > 1000, "The placed block catalogue was not checked");
        System.out.println("BUILDING_PIECES_FLAT_STATES_PASS states=" + checked);
    }

    static CompoundTag chunk(int number, int code, String material) {
        CompoundTag root = new CompoundTag();

        root.putInt("DataVersion", 1343);

        CompoundTag level = new CompoundTag();

        level.putInt("xPos", 0);
        level.putInt("zPos", 0);
        level.putBoolean("TerrainPopulated", true);
        level.putBoolean("LightPopulated", true);
        level.putLong("LastUpdate", 0);
        level.putByteArray("Biomes", new byte[256]);
        level.putIntArray("HeightMap", new int[256]);

        CompoundTag section = new CompoundTag();

        section.putByte("Y", (byte) 4);

        byte[] low = new byte[4096];
        byte[] high = new byte[2048];
        byte[] values = new byte[2048];

        low[0] = (byte) number;
        LegacyBridge.setNibble(high, 0, number >> 8);
        LegacyBridge.setNibble(values, 0, code);
        section.putByteArray("Blocks", low);
        section.putByteArray("Add", high);
        section.putByteArray("Data", values);
        section.putByteArray("BlockLight", new byte[2048]);
        section.putByteArray("SkyLight", new byte[2048]);

        ListTag sections = new ListTag();

        sections.add(section);
        level.put("Sections", sections);

        CompoundTag tile = new CompoundTag();

        tile.putString("id", "buildingbricks:material");
        tile.putString("material", material);
        tile.putString("custom", "Retain complete tile data");
        tile.putInt("x", 0);
        tile.putInt("y", 64);
        tile.putInt("z", 0);

        ListTag tiles = new ListTag();

        tiles.add(tile);
        level.put("TileEntities", tiles);
        level.put("Entities", new ListTag());
        root.put("Level", level);
        return root;
    }

    private static boolean contains(CompoundTag level, BlockState state) {
        ListTag sections = level.getList("Sections", 10);
        CompoundTag expected = NbtUtils.writeBlockState(state);

        for (int section = 0; section < sections.size(); section++) {
            ListTag palette = sections.getCompound(section).getList("Palette", 10);

            for (int index = 0; index < palette.size(); index++) {
                if (palette.getCompound(index).equals(expected)) {
                    return true;
                }
            }
        }

        return false;
    }

    static CompoundTag stack(String material, int count) {
        CompoundTag raw = new CompoundTag();

        raw.putString("id", "buildingbricks:rock_step");
        raw.putShort("Damage", (short) 327);
        raw.putByte("Count", (byte) count);
        raw.putByte("cabinetSlot", (byte) 4);

        CompoundTag tag = new CompoundTag();

        tag.putString("material", material);
        tag.putString("custom", "Keep this data");
        raw.put("tag", tag);
        return raw;
    }

    private static void storedInventories(ServerLevel world) {
        CompoundTag root = chunk(LegacyTestScope.STEP_ID, 0, "minecraft:stone");
        CompoundTag level = root.getCompound("Level");
        CompoundTag markers = new CompoundTag();

        markers.putInt("vanilla", 1);
        markers.putInt("dirt_vertical_slabs", 1);
        level.put("skysbuildingpieces_coverage", markers);

        CompoundTag cabinet = new CompoundTag();

        cabinet.putString("id", "cfm:cabinet");

        ListTag contents = new ListTag();
        CompoundTag raw = stack("minecraft:stone", 17);
        CompoundTag custom = raw.getCompound("tag").copy();

        contents.add(raw);
        cabinet.put("cabinetItems", contents);
        level.getList("TileEntities", 10).add(cabinet);
        LegacyBridge.prepareChunk(world, root);
        require(raw.getString("id").startsWith("skysbuildingpieces:"), "Raw inventory in an already covered chunk");
        require(raw.getByte("Count") == 17 && raw.getByte("cabinetSlot") == 4 && raw.getCompound("tag").equals(custom),
                "Container slot, count and unrelated NBT");

        ItemStack decoded = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(raw);

        require(!decoded.isEmpty() && decoded.getCount() == 17, "Recovered stack deserializes normally");

        CompoundTag once = root.copy();

        LegacyBridge.prepareChunk(world, root);
        require(once.equals(root), "Covered inventory reload is idempotent");

        CompoundTag player = new CompoundTag();
        ListTag inventory = new ListTag();

        inventory.add(stack("minecraft:stone", 9));
        player.put("Inventory", inventory);

        ListTag ender = new ListTag();

        ender.add(stack("minecraft:stone", 11));
        player.put("EnderItems", ender);

        CompoundTag fixed = DataFixTypes.PLAYER.update(world.getServer().getFixerUpper(), player, 1343,
                1976);

        require(fixed.getList("Inventory", 10).getCompound(0).getString("id").startsWith("skysbuildingpieces:")
                && fixed.getList("EnderItems", 10).getCompound(0).getByte("Count") == 11,
                "Early native player hook covers main and ender inventories");
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_RAW_CONTAINER_PASS covered_chunk=true nonstandard_inventory_key=true");
    }

    private static void unknownContentStops() {
        CompoundTag bad = stack("missing:unknown", 3);

        assertAbort(bad, false);

        CompoundTag crate = NativeProbeItems.save(new ItemStack(Blocks.CHEST));
        ListTag contents = new ListTag();

        contents.add(stack("minecraft:stone", 7));
        contents.add(bad.copy());

        CompoundTag tag = new CompoundTag();

        tag.put("Items", contents);
        crate.put("tag", tag);
        assertAbort(crate, false);

        CompoundTag chunk = chunk(LegacyTestScope.STEP_ID, 0, "missing:unknown");

        assertAbort(chunk, true);
    }

    private static void assertAbort(CompoundTag value, boolean chunk) {
        CompoundTag before = value.copy();
        boolean stopped = false;

        try {
            if (chunk) {
                LegacyBridge.prepareLegacyChunk(value);
            } else {
                LegacyBridge.prepareStack(value);
            }
        } catch (LegacyBridge.RecoveryAbort expected) {
            stopped = expected.getMessage().contains("missing:unknown");
        }

        require(stopped && before.equals(value), "Unsafe recovery stops before changing storage");
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
