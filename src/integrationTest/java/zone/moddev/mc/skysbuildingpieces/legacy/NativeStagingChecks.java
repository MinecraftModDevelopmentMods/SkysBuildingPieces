package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.world.server.ServerWorld;

/** Check the vanilla fixers which still run after our numeric lookup. */
public final class NativeStagingChecks {
    private NativeStagingChecks() {
    }

    public static void run(ServerWorld world) throws Exception {
        int checked = 0;

        try (LegacyTestScope ignored = new LegacyTestScope(world)) {
            stoneSlabItems(world);
            for (BlockState expected : new BlockState[] {
                    Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                    Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false),
                    Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                    Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false),
                    Blocks.KELP.defaultBlockState(),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.block.SlabBlock.TYPE,
                            net.minecraft.state.properties.SlabType.TOP),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.block.SlabBlock.TYPE,
                            net.minecraft.state.properties.SlabType.BOTTOM) }) {
                int number = NumericStateBridge.encode(expected);
                CompoundNBT root = new CompoundNBT();
                CompoundNBT level = new CompoundNBT();
                CompoundNBT section = new CompoundNBT();
                byte[] blocks = new byte[4096];
                byte[] high = new byte[2048];
                byte[] data = new byte[2048];

                blocks[0] = (byte) (number >>> 4);
                LegacyBridge.setNibble(high, 0, number >>> 12);
                LegacyBridge.setNibble(data, 0, number & 15);
                section.putByte("Y", (byte) 4);
                section.putByteArray("Blocks", blocks);
                section.putByteArray("Add", high);
                section.putByteArray("Data", data);
                section.putByteArray("BlockLight", new byte[2048]);
                section.putByteArray("SkyLight", new byte[2048]);

                ListNBT sections = new ListNBT();

                sections.add(section);
                level.put("Sections", sections);
                level.putByteArray("Biomes", new byte[256]);
                level.putInt("xPos", 0);
                level.putInt("zPos", 0);
                level.putBoolean("TerrainPopulated", true);
                level.putBoolean("LightPopulated", true);
                level.putLong("LastUpdate", 0);
                level.putIntArray("HeightMap", new int[256]);
                level.put("Entities", new ListNBT());
                level.put("TileEntities", new ListNBT());
                root.put("Level", level);
                root.putInt("DataVersion", 1343);

                for (boolean intermediate : new boolean[] { false, true }) {
                    CompoundNBT source = root.copy();
                    if (intermediate) {
                        source = NBTUtil.update(world.getServer().getFixerUpper(), DefaultTypeReferences.CHUNK, source,
                                1343, 1493);
                    }
                    CompoundNBT fixed = NBTUtil.update(world.getServer().getFixerUpper(), DefaultTypeReferences.CHUNK,
                            source, intermediate ? 1493 : 1343, 1976);
                    ListNBT palette = fixed.getCompound("Level").getList("Sections", 10).getCompound(0)
                            .getList("Palette", 10);

                    if (!palette.contains(NBTUtil.writeBlockState(expected))) {
                        throw new AssertionError("Vanilla data fixing changed the staged state " + expected
                                + " intermediate=" + intermediate + ": " + palette);
                    }
                }

                checked++;
            }
        }

        System.out.println("BUILDING_PIECES_NATIVE_STAGING_PASS states=" + checked
                + " pipelines=2 persistent=true kelp_tip=true");
    }

    private static void stoneSlabItems(ServerWorld world) {
        zone.moddev.mc.skysbuildingpieces.content.PieceBlock source = zone.moddev.mc.skysbuildingpieces.content.Pieces
                .BLOCKS.values().stream()
                .filter(block -> block.palette.shape == zone.moddev.mc.skysbuildingpieces.catalogue.Shape.SLAB
                        && block.palette.materials.contains("minecraft:stone"))
                .findFirst().orElseThrow(() -> new AssertionError("The permanent rough-stone palette is missing"));

        for (int orientation = 0; orientation < 2; orientation++) {
            CompoundNBT item = new CompoundNBT();
            CompoundNBT custom = new CompoundNBT();

            custom.putString("fixture", "Rough stone stays rough");
            item.put("tag", custom);
            item.putString("id", source.getRegistryName().toString());
            item.putShort("Damage", (short) (source.palette.materials.indexOf("minecraft:stone") * 2 + orientation));
            item.putByte("Count", (byte) 37);
            item.putByte("Slot", (byte) 7);

            ListNBT inventory = new ListNBT();
            inventory.add(item);
            CompoundNBT player = new CompoundNBT();
            player.put("Inventory", inventory);

            for (boolean intermediate : new boolean[] { false, true }) {
                CompoundNBT sourceData = player.copy();
                if (intermediate) {
                    sourceData = NBTUtil.update(world.getServer().getFixerUpper(), DefaultTypeReferences.PLAYER,
                            sourceData, 1343, 1493);
                }
                CompoundNBT upgraded = NBTUtil.update(world.getServer().getFixerUpper(), DefaultTypeReferences.PLAYER,
                        sourceData, intermediate ? 1493 : 1343, 1976).getList("Inventory", 10).getCompound(0);
                net.minecraft.item.ItemStack result = net.minecraft.item.ItemStack.of(upgraded);

                if (result.getItem() != net.minecraft.item.Items.STONE_SLAB || result.getCount() != 37
                        || !result.hasTag() || !custom.equals(result.getTag()) || upgraded.getByte("Slot") != 7
                        || upgraded.contains(HistoricalItems.NATIVE_ITEM_ID)) {
                    throw new AssertionError("Vanilla data fixing changed the recovered rough-stone slab item"
                            + " intermediate=" + intermediate + ": " + upgraded);
                }
            }
        }

        System.out.println("BUILDING_PIECES_NATIVE_STONE_ITEMS_PASS orientations=2 counts_nbt_slot=true");
    }
}
