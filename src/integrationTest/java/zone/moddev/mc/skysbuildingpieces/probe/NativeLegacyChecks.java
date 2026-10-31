package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyTestScope;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Exercises raw recovery before the target's native data fixer reads a chunk. */
public final class NativeLegacyChecks {
    private NativeLegacyChecks() {
    }

    public static void run(ServerWorld world) throws Exception {
        zone.moddev.mc.skysbuildingpieces.legacy.NativeStagingChecks.run(world);

        boolean forced = SkysBuildingPieces.forceReplace;
        boolean installed = ModList.get().isLoaded("buildingbricks");

        try (LegacyTestScope source = new LegacyTestScope(world)) {
            SkysBuildingPieces.forceReplace = true;

            for (int code = 0; code < 16; code++) {
                CompoundNBT raw = chunk(LegacyTestScope.STEP_ID, code, "minecraft:stone");

                LegacyBridge.prepareLegacyChunk(raw);

                CompoundNBT level = raw.getCompound("Level");
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

                CompoundNBT once = raw.copy();

                LegacyBridge.prepareLegacyChunk(raw);
                require(once.equals(raw), "Raw chunk recovery is idempotent");

                CompoundNBT fixed = NBTUtil.update(world.getServer().getDataFixer(), DefaultTypeReferences.CHUNK,
                        raw.copy(), 1343, 1976);

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

                CompoundNBT raw = chunk(LegacyTestScope.STEP_ID, 11, "minecraft:stone");
                CompoundNBT before = raw.copy();

                LegacyBridge.prepareLegacyChunk(raw);
                require(raw.equals(before), "Installed/off must leave raw blocks, items and coverage unchanged");
                SkysBuildingPieces.forceReplace = true;
            }

            // Already flattened saves must never enter numeric recovery.
            CompoundNBT modern = new CompoundNBT();

            modern.putInt("DataVersion", 1519);
            modern.put("Level", new CompoundNBT());

            CompoundNBT before = modern.copy();

            LegacyBridge.prepareLegacyChunk(modern);
            require(modern.equals(before), "Named-state chunks bypass the numeric bridge");
            CoverageContributor.verify(world);
        } finally {
            SkysBuildingPieces.forceReplace = forced;
        }

        System.out
                .println("BUILDING_PIECES_NATIVE_LEGACY_PASS step_codes=16 native_datafix=true installed=" + installed);
    }

    static CompoundNBT chunk(int number, int code, String material) {
        CompoundNBT root = new CompoundNBT();

        root.putInt("DataVersion", 1343);

        CompoundNBT level = new CompoundNBT();

        level.putInt("xPos", 0);
        level.putInt("zPos", 0);
        level.putBoolean("TerrainPopulated", true);
        level.putBoolean("LightPopulated", true);
        level.putLong("LastUpdate", 0);
        level.putByteArray("Biomes", new byte[256]);
        level.putIntArray("HeightMap", new int[256]);

        CompoundNBT section = new CompoundNBT();

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

        ListNBT sections = new ListNBT();

        sections.add(section);
        level.put("Sections", sections);

        CompoundNBT tile = new CompoundNBT();

        tile.putString("id", "buildingbricks:material");
        tile.putString("material", material);
        tile.putString("custom", "Retain complete tile data");
        tile.putInt("x", 0);
        tile.putInt("y", 64);
        tile.putInt("z", 0);

        ListNBT tiles = new ListNBT();

        tiles.add(tile);
        level.put("TileEntities", tiles);
        level.put("Entities", new ListNBT());
        root.put("Level", level);
        return root;
    }

    private static boolean contains(CompoundNBT level, BlockState state) {
        ListNBT sections = level.getList("Sections", 10);
        CompoundNBT expected = NBTUtil.writeBlockState(state);

        for (int section = 0; section < sections.size(); section++) {
            ListNBT palette = sections.getCompound(section).getList("Palette", 10);

            for (int index = 0; index < palette.size(); index++) {
                if (palette.getCompound(index).equals(expected)) {
                    return true;
                }
            }
        }

        return false;
    }

    static CompoundNBT stack(String material, int count) {
        CompoundNBT raw = new CompoundNBT();

        raw.putString("id", "buildingbricks:rock_step");
        raw.putShort("Damage", (short) 327);
        raw.putByte("Count", (byte) count);
        raw.putByte("cabinetSlot", (byte) 4);

        CompoundNBT tag = new CompoundNBT();

        tag.putString("material", material);
        tag.putString("custom", "Keep this data");
        raw.put("tag", tag);
        return raw;
    }

    private static void storedInventories(ServerWorld world) {
        CompoundNBT root = chunk(LegacyTestScope.STEP_ID, 0, "minecraft:stone");
        CompoundNBT level = root.getCompound("Level");
        CompoundNBT markers = new CompoundNBT();

        markers.putInt("vanilla", 1);
        markers.putInt("dirt_vertical_slabs", 1);
        level.put("skysbuildingpieces_coverage", markers);

        CompoundNBT cabinet = new CompoundNBT();

        cabinet.putString("id", "cfm:cabinet");

        ListNBT contents = new ListNBT();
        CompoundNBT raw = stack("minecraft:stone", 17);
        CompoundNBT custom = raw.getCompound("tag").copy();

        contents.add(raw);
        cabinet.put("cabinetItems", contents);
        level.getList("TileEntities", 10).add(cabinet);
        LegacyBridge.prepareChunk(world, root);
        require(raw.getString("id").startsWith("skysbuildingpieces:"), "Raw inventory in an already covered chunk");
        require(raw.getByte("Count") == 17 && raw.getByte("cabinetSlot") == 4 && raw.getCompound("tag").equals(custom),
                "Container slot, count and unrelated NBT");

        ItemStack decoded = ItemStack.read(raw);

        require(!decoded.isEmpty() && decoded.getCount() == 17, "Recovered stack deserializes normally");

        CompoundNBT once = root.copy();

        LegacyBridge.prepareChunk(world, root);
        require(once.equals(root), "Covered inventory reload is idempotent");

        CompoundNBT player = new CompoundNBT();
        ListNBT inventory = new ListNBT();

        inventory.add(stack("minecraft:stone", 9));
        player.put("Inventory", inventory);

        ListNBT ender = new ListNBT();

        ender.add(stack("minecraft:stone", 11));
        player.put("EnderItems", ender);

        CompoundNBT fixed = NBTUtil.update(world.getServer().getDataFixer(), DefaultTypeReferences.PLAYER, player, 1343,
                1976);

        require(fixed.getList("Inventory", 10).getCompound(0).getString("id").startsWith("skysbuildingpieces:")
                && fixed.getList("EnderItems", 10).getCompound(0).getByte("Count") == 11,
                "Early native player hook covers main and ender inventories");
        System.out.println("BUILDING_PIECES_RAW_CONTAINER_PASS covered_chunk=true nonstandard_inventory_key=true");
    }

    private static void unknownContentStops() {
        CompoundNBT bad = stack("missing:unknown", 3);

        assertAbort(bad, false);

        CompoundNBT crate = new ItemStack(Blocks.CHEST).write(new CompoundNBT());
        ListNBT contents = new ListNBT();

        contents.add(stack("minecraft:stone", 7));
        contents.add(bad.copy());

        CompoundNBT tag = new CompoundNBT();

        tag.put("Items", contents);
        crate.put("tag", tag);
        assertAbort(crate, false);

        CompoundNBT chunk = chunk(LegacyTestScope.STEP_ID, 0, "missing:unknown");

        assertAbort(chunk, true);
    }

    private static void assertAbort(CompoundNBT value, boolean chunk) {
        CompoundNBT before = value.copy();
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
