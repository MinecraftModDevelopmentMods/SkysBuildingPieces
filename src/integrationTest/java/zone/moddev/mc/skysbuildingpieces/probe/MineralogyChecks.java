package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.Block;
import net.minecraft.state.IProperty;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

final class MineralogyChecks {
    private MineralogyChecks() {
    }

    static void run() {
        Catalogue.Module rocks = Catalogue.INSTANCE.modules.get("mineralogy");

        if (!ModList.get().isLoaded("mineralogy")) {
            require(rocks == null, "Mineralogy pieces registered without Mineralogy");
            return;
        }

        require(!ModList.get().isLoaded("skysbuildingpiecesmineralogy"), "the separate add-on is still installed");
        require(rocks != null && rocks.palettes.size() == 35, "the built-in catalogue is missing");

        int registered = 0;

        for (Block block : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValues()) {
            if (block.getRegistryName().toString().startsWith("skysbuildingpieces:mineralogy_")) {
                registered++;
            }
        }

        require(registered == 35, "Mineralogy must use exactly 35 core block IDs");

        int states = 0;

        for (Catalogue.MaterialDef material : rocks.materials.values()) {
            BlockState bottom = Pieces.state(material.id, Shape.SLAB, 1);
            BlockState top = Pieces.state(material.id, Shape.SLAB, 0);

            if (bottom == null) {
                require(top == null, "disabled native slab has only one half");
                continue;
            }

            require(facing(bottom) == Direction.UP && facing(top) == Direction.DOWN, "native slab orientation changed");
            require(Pieces.stack(bottom, 1).getDamage() == 0, "native slab item metadata changed");
        }
        for (Catalogue.Palette palette : rocks.palettes) {
            for (String material : palette.materials) {
                for (int orientation = 0; orientation < palette.shape.states; orientation++) {
                    BlockState state = Pieces.state(material, palette.shape, orientation);

                    require(state != null && !state.getBlock().hasTileEntity(state), "invalid rock piece");
                    require(((zone.moddev.mc.skysbuildingpieces.content.PieceBlock) state.getBlock())
                            .getMetaFromState(state) == palette.meta(material, orientation),
                            "saved material or orientation changed");
                    states++;
                }
            }
        }

        require(states == 540, "not all rock states were checked");
        rawInventories();
        System.out.println("MINERALOGY_CORE_PASS palettes=" + registered + " states=" + states + " addon=false");
    }

    private static Direction facing(BlockState state) {
        for (IProperty<?> property : state.getProperties()) {
            if (property.getName().equals("facing")) {
                return (Direction) state.get(property);
            }
        }

        throw new AssertionError("Native slab has no facing property");
    }

    private static void rawInventories() {
        net.minecraft.nbt.CompoundNBT stack = new net.minecraft.nbt.CompoundNBT();

        stack.putString("id", "skysbuildingpiecesmineralogy:rock_horizontal_step_00");
        stack.putByte("Count", (byte) 37);
        stack.putShort("Damage", (short) 11);

        net.minecraft.nbt.CompoundNBT tag = new net.minecraft.nbt.CompoundNBT();

        tag.putString("unchanged", "custom data");
        stack.put("tag", tag);

        net.minecraft.nbt.CompoundNBT expected = stack.copy();

        expected.putString("id", "skysbuildingpieces:mineralogy_rock_horizontal_step_00");

        net.minecraft.nbt.CompoundNBT container = new net.minecraft.nbt.CompoundNBT();

        container.put("sealed_contents", stack);
        zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.prepareStacks(container);
        require(stack.equals(expected), "raw recovery changed counts, metadata or unrelated NBT");
        zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.prepareStacks(container);
        require(stack.equals(expected), "raw recovery is not idempotent");

        net.minecraft.nbt.CompoundNBT valid = expected.copy();

        valid.putString("id", "skysbuildingpiecesmineralogy:rock_horizontal_step_00");

        net.minecraft.nbt.CompoundNBT invalid = valid.copy();

        invalid.putShort("Damage", (short) -1);
        container.put("valid", valid);
        container.put("invalid", invalid);

        net.minecraft.nbt.CompoundNBT before = container.copy();
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
