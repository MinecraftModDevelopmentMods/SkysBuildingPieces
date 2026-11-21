package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.PieceItem;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Query Forge's real fuel path, including the general query used by recipe viewers. */
public final class FuelChecks {
    public static void run() {
        List<String> failures = new ArrayList<>();
        int wooden = 0;
        int nonWooden = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof PieceItem piece)) {
                continue;
            }
            Catalogue.MaterialDef material = Catalogue.INSTANCE.materials.get(piece.material);
            boolean wood = material.type.equals("wood");
            if (wood) {
                wooden++;
            } else {
                nonWooden++;
            }
            ItemStack full = Pieces.stack(Pieces.nativeState(material, "full"), 1);
            int divisor = switch (piece.piece.palette.shape) {
                case SLAB, VERTICAL_SLAB -> 2;
                case STEP, HORIZONTAL_STEP, VERTICAL_STEP -> 4;
                case CORNER -> 8;
                default -> 1;
            };
            for (RecipeType<?> cooking : Arrays.<RecipeType<?>>asList(
                    null, RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.BLASTING)) {
                ItemStack stack = new ItemStack(item);
                int expected = wood && full != null ? ForgeHooks.getBurnTime(full, cooking) / divisor : 0;
                int actual = ForgeHooks.getBurnTime(stack, cooking);
                if ((actual != expected || piece.getBurnTime(stack, cooking) < 0) && failures.size() < 12) {
                    failures.add(ForgeRegistries.ITEMS.getKey(item) + " type=" + cooking
                            + " expected=" + expected + " actual=" + actual
                            + " direct=" + piece.getBurnTime(stack, cooking));
                }
            }
        }
        if (wooden == 0 || nonWooden == 0 || !failures.isEmpty()) {
            throw new AssertionError("Piece fuel queries failed: " + failures);
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_NATIVE_FUEL_PASS wood={} other={} cooking_types=4", wooden, nonWooden);
    }

    private FuelChecks() {
    }
}
