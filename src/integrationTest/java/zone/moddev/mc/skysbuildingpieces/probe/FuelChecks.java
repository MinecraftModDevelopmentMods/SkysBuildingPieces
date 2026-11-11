package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import zone.moddev.mc.skysbuildingpieces.content.PieceItem;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Cutting and recombining wood must not multiply its furnace fuel value. */
final class FuelChecks {
    static void run() {
        int cases = 0;
        for (var block : Pieces.playableBlocks()) {
            var definition = block.definition(block.defaultBlockState());
            if (!definition.type.equals("wood")) {
                continue;
            }
            PieceItem item = (PieceItem) block.asItem();
            ItemStack full = Pieces.stack(Pieces.nativeState(definition, "full"), 1);
            int divisor = switch (block.palette.shape) {
                case SLAB, VERTICAL_SLAB -> 2;
                case STEP, HORIZONTAL_STEP, VERTICAL_STEP -> 4;
                case CORNER -> 8;
                default -> 1;
            };
            for (var furnace : java.util.List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.BLASTING)) {
                int original = Math.max(0, full.getBurnTime(furnace));
                int actual = new ItemStack(item).getBurnTime(furnace);
                if (actual != original / divisor) {
                    throw new AssertionError("Wood fuel changed through cutting: " + definition.id + "/"
                            + block.palette.shape + " expected=" + original / divisor + " actual=" + actual);
                }
                cases++;
            }
        }
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_FUEL_PASS cases=" + cases);
    }

    private FuelChecks() {
    }
}

