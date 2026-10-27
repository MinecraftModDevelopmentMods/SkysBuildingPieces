package zone.moddev.mc.skysbuildingpieces.mineralogytest;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Verifies real Forge remapping, including chunks first opened after the initial upgrade save. */
final class LegacyInventoryChecks {
    private LegacyInventoryChecks() {
    }

    static void run(WorldServer world, String phase) {
        if (!Boolean.getBoolean("mineralogycore.upgrade")) {
            return;
        }
        verify(world, 0);
        if (phase.contains("reload")) {
            world.getChunkFromChunkCoords(40, 40);
            verify(world, 640);
        }
    }

    private static void verify(WorldServer world, int offset) {
        int index = 0;
        int units = 0;
        for (Catalogue.Palette palette : Catalogue.INSTANCE.modules.get("mineralogy").palettes) {
            for (String material : palette.materials) {
                for (int orientation = 0; orientation < palette.shape.states; orientation++) {
                    IBlockState expected = Pieces.state(material, palette.shape, orientation);
                    BlockPos position = new BlockPos(offset + (index & 15),
                            100 + (index >> 8), offset + ((index >> 4) & 15));
                    RuntimeProbe.require(world.getBlockState(position).equals(expected),
                            "Legacy block or orientation changed at " + position);
                    int chest = index / 27;
                    BlockPos chestPosition = new BlockPos(offset + (chest & 15), 108, offset + (chest >> 4));
                    RuntimeProbe.require(world.getTileEntity(chestPosition) instanceof TileEntityChest,
                            "Missing fixture inventory at " + chestPosition + "; block="
                                    + world.getBlockState(chestPosition) + "; tile=" + world.getTileEntity(chestPosition));
                    ItemStack stack = ((TileEntityChest) world.getTileEntity(chestPosition)).getStackInSlot(index % 27);
                    RuntimeProbe.require(stack != null, "Legacy piece item disappeared");
                    ItemStack wanted = Pieces.stack(expected, 1);
                    RuntimeProbe.require(stack.getItem() == wanted.getItem()
                                    && stack.getMetadata() == palette.meta(material, orientation),
                            "Legacy item identity or raw metadata changed at stack " + index
                                    + ": expected " + palette.registryId() + " metadata "
                                    + palette.meta(material, orientation) + ", got " + stack);
                    RuntimeProbe.require(stack.stackSize == 1 + index % 64, "Legacy stack count changed");
                    RuntimeProbe.require(stack.getTagCompound() != null
                                    && stack.getTagCompound().getInteger("fixture_index") == index
                                    && stack.getTagCompound().getCompoundTag("display").getString("Name")
                                            .equals("Fixture rock piece " + index)
                                    && stack.getTagCompound().getTagList("ench", 10).getCompoundTagAt(0).getShort("lvl") == 2,
                            "Legacy custom item data changed");
                    units += stack.stackSize;
                    index++;
                }
            }
        }
        RuntimeProbe.require(index == 540, "Incomplete legacy inventory audit");
        System.out.println("MINERALOGY_LEGACY_CONTENT_PASS offset=" + offset + " states=" + index
                + " stacks=" + index + " units=" + units + " custom_nbt=true");
    }
}
