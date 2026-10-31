package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Genuine BuildingBricks save/reload probe; no production dependency. */
public final class LegacyFixture {
    private static final String[] IDS = { "rock_step", "wood_vertical_slab", "glass_slab", "dirt_stairs", "wood_corner",
            "rock_wall", "ice_pane" };
    private static final String[] MATERIALS = { "minecraft:stone", "minecraft:planks_oak", "minecraft:glass",
            "minecraft:dirt", "minecraft:planks_spruce", "minecraft:stonebrick", "minecraft:ice" };

    private static BlockPos pos(int entryIndex) {
        return new BlockPos(800 + (entryIndex & 15), 70, 800 + (entryIndex >> 4));
    }

    public static void run(ServerWorld world, String phase) throws Exception {
        boolean source = phase.equals("legacy-source");
        boolean converted = phase.equals("legacy-converted") || phase.equals("legacy-reload")
                || phase.equals("legacy-disabled-after");

        if (source) {
            require(!LegacyBridge.active(), "source must leave old content alone");

            for (int entryIndex = 0; entryIndex < 26; entryIndex++) {
                int family = entryIndex < 16 ? 0 : entryIndex - 15;
                int meta = entryIndex < 16 ? entryIndex : family == 1 ? 3 : 0;

                String legacyId = entryIndex < 22 ? IDS[family] : "dirt_vertical_slab";
                String material = entryIndex < 22 ? MATERIALS[family] : "minecraft:dirt";

                if (entryIndex >= 22) {
                    meta = entryIndex - 22;
                }

                Block legacyBlock = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                        .getValue(new net.minecraft.util.ResourceLocation("buildingbricks:" + legacyId));

                require(legacyBlock != null, "real legacy block " + legacyId);
                world.setBlockState(pos(entryIndex), legacyBlock.getDefaultState().with(
                        (net.minecraft.state.IntegerProperty) legacyBlock.getStateContainer().getProperty("meta"),
                        meta), 2);

                TileEntity tile = world.getTileEntity(pos(entryIndex));

                require(tile != null, "real material tile " + legacyId);

                CompoundNBT compoundTag = tile.write(new CompoundNBT());

                compoundTag.putString("material", material);
                compoundTag.putString("fixture_note", "material identity, not item damage");
                tile.read(compoundTag);
                tile.markDirty();
            }

            world.setBlockState(pos(32), Blocks.CHEST.getDefaultState(), 2);

            ChestTileEntity chest = (ChestTileEntity) world.getTileEntity(pos(32));

            chest.setInventorySlotContents(0, legacyStack("rock_step", "minecraft:stone", 23));
            chest.setInventorySlotContents(1, legacyStack("wood_corner", "minecraft:planks_oak", 7));
            world.addEntity(new ItemEntity(world, 804, 72, 804, legacyStack("rock_step", "minecraft:stone", 5)));
        } else {
            for (int entryIndex = 0; entryIndex < 26; entryIndex++) {
                int family = entryIndex < 16 ? 0 : entryIndex - 15;
                int meta = entryIndex < 16 ? entryIndex : family == 1 ? 3 : 0;

                BlockState state = world.getBlockState(pos(entryIndex));
                String legacyId = entryIndex < 22 ? IDS[family] : "dirt_vertical_slab";
                String material = entryIndex < 22 ? MATERIALS[family] : "minecraft:dirt";

                if (entryIndex >= 22) {
                    meta = entryIndex - 22;
                }
                if (converted) {
                    Shape shape = LegacyMapping.shape("buildingbricks:" + legacyId, meta);
                    BlockState expected = Pieces.state(material, shape, LegacyMapping.orientation(shape, meta));

                    require(state.equals(expected.getBlock() instanceof PieceBlock
                            ? ((PieceBlock) expected.getBlock()).getActualState(expected, world, pos(entryIndex))
                            : expected),
                            "converted identity and geometry " + entryIndex + " " + state + " != " + expected);
                    require(world.getTileEntity(pos(entryIndex)) == null, "no remaining material tile " + entryIndex);
                } else {
                    require(state.getBlock().getRegistryName().toString().equals("buildingbricks:" + legacyId),
                            "default coexistence block " + entryIndex);

                    TileEntity tile = world.getTileEntity(pos(entryIndex));

                    require(tile != null && tile.write(new CompoundNBT()).getString("material").equals(material),
                            "default material identity");
                }
            }

            ChestTileEntity chest = (ChestTileEntity) world.getTileEntity(pos(32));
            ItemStack stack = chest.getStackInSlot(0);

            require(stack != null && stack.getCount() == 23
                    && stack.getTag().getString("custom_label").equals("NBT survives"),
                    "container count and custom NBT");
            require(stack.getItem().getRegistryName().getNamespace()
                    .equals(converted ? "skysbuildingpieces" : "buildingbricks"), "container identity");

            CompoundNBT saved = new CompoundNBT();

            world.getSavedData().save();

            if (converted) {
                PiecesWorldState state = PiecesWorldState.get(world);

                require(state.blocks == 26, "block counter " + state.blocks);
                require(state.items == 35, "item counter " + state.items);
            }
        }

        Properties result = new Properties();

        result.setProperty("phase", phase);
        result.setProperty("forced", Boolean.toString(SkysBuildingPieces.forceReplace));
        result.setProperty("blocks", "26");

        try (OutputStream output = new FileOutputStream(
                new File(world.getSaveHandler().getWorldDirectory(), phase + ".properties"))) {
            result.store(output, "Disposable fixture verification");
        }

        System.out.println("BUILDING_PIECES_LEGACY_PASS " + phase);
    }

    private static ItemStack legacyStack(String registryId, String material, int count) {
        CompoundNBT compoundTag = new CompoundNBT();

        compoundTag.putString("id", "buildingbricks:" + registryId);
        compoundTag.putByte("Count", (byte) count);
        compoundTag.putShort("Damage", (short) 0);

        CompoundNBT tag = new CompoundNBT();

        tag.putString("material", material);
        tag.putString("custom_label", "NBT survives");
        compoundTag.put("tag", tag);
        return ItemStack.read(compoundTag);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
