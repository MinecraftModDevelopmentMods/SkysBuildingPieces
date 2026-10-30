package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

final class MineralogyChecks {
    private MineralogyChecks() {
    }

    static void run() {
        Catalogue.Module rocks = Catalogue.INSTANCE.modules.get("mineralogy");
        if (!Loader.isModLoaded("mineralogy")) {
            require(rocks == null, "Mineralogy pieces registered without Mineralogy");
            return;
        }
        require(!Loader.isModLoaded("skysbuildingpiecesmineralogy"), "the separate add-on is still installed");
        require(rocks != null && rocks.palettes.size() == 35, "the built-in catalogue is missing");
        int registered = 0;
        for (Block block : Block.REGISTRY) {
            if (block.getRegistryName().toString().startsWith("skysbuildingpieces:mineralogy_")) {
                registered++;
            }
        }
        require(registered == 35, "Mineralogy must use exactly 35 core block IDs");
        int states = 0;
        for (Catalogue.MaterialDef material : rocks.materials.values()) {
            IBlockState bottom = Pieces.state(material.id, Shape.SLAB, 1);
            IBlockState top = Pieces.state(material.id, Shape.SLAB, 0);
            if (bottom == null) {
                require(top == null, "disabled native slab has only one half");
                continue;
            }
            require(facing(bottom) == EnumFacing.UP && facing(top) == EnumFacing.DOWN,
                    "native slab orientation changed");
            require(Pieces.stack(bottom, 1).getItemDamage() == 0, "native slab item metadata changed");
        }
        for (Catalogue.Palette palette : rocks.palettes) {
            for (String material : palette.materials) {
                for (int orientation = 0; orientation < palette.shape.states; orientation++) {
                    IBlockState state = Pieces.state(material, palette.shape, orientation);
                    require(state != null && !state.getBlock().hasTileEntity(state), "invalid rock piece");
                    require(state.getBlock().getMetaFromState(state) == palette.meta(material, orientation),
                            "saved material or orientation changed");
                    states++;
                }
            }
        }
        require(states == 540, "not all rock states were checked");
        rawInventories();
        System.out.println("MINERALOGY_CORE_PASS palettes=" + registered + " states=" + states + " addon=false");
    }

    private static EnumFacing facing(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property.getName().equals("facing")) {
                return (EnumFacing) state.getProperties().get(property);
            }
        }
        throw new AssertionError("Native slab has no facing property");
    }

    private static void rawInventories() {
        net.minecraft.nbt.NBTTagCompound stack = new net.minecraft.nbt.NBTTagCompound();
        stack.setString("id", "skysbuildingpiecesmineralogy:rock_horizontal_step_00");
        stack.setByte("Count", (byte) 37);
        stack.setShort("Damage", (short) 11);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setString("unchanged", "custom data");
        stack.setTag("tag", tag);
        net.minecraft.nbt.NBTTagCompound expected = stack.copy();
        expected.setString("id", "skysbuildingpieces:mineralogy_rock_horizontal_step_00");

        net.minecraft.nbt.NBTTagCompound container = new net.minecraft.nbt.NBTTagCompound();
        container.setTag("sealed_contents", stack);
        zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.prepareStacks(container);
        require(stack.equals(expected), "raw recovery changed counts, metadata or unrelated NBT");
        zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.prepareStacks(container);
        require(stack.equals(expected), "raw recovery is not idempotent");

        net.minecraft.nbt.NBTTagCompound valid = expected.copy();
        valid.setString("id", "skysbuildingpiecesmineralogy:rock_horizontal_step_00");
        net.minecraft.nbt.NBTTagCompound invalid = valid.copy();
        invalid.setShort("Damage", (short) -1);
        container.setTag("valid", valid);
        container.setTag("invalid", invalid);
        net.minecraft.nbt.NBTTagCompound before = container.copy();
        boolean stopped = false;
        try {
            zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.prepareStacks(container);
        } catch (IllegalStateException expectedFailure) {
            stopped = true;
        }
        require(stopped && container.equals(before), "invalid nested recovery must stop without partial writes");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
