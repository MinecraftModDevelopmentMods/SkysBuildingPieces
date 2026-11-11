package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import zone.moddev.mc.skysbuildingpieces.content.PieceItem;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Recipe viewers ask about every item, including pieces that cannot burn. */
public final class FuelChecks {
    public static void run() {
        reportedItems();
        configuredNonWoodFuel();
        int woodCases = 0;
        int otherCases = 0;
        for (var registered : BuiltInRegistries.ITEM) {
            if (!(registered instanceof PieceItem item)) {
                continue;
            }
            var definition = zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.materials.get(item.material);
            boolean wooden = definition.type.equals("wood");
            ItemStack full = wooden ? Pieces.stack(Pieces.nativeState(definition, "full"), 1) : ItemStack.EMPTY;
            var dataFuel = item.builtInRegistryHolder().getData(NeoForgeDataMaps.FURNACE_FUELS);
            int defaultFuel = dataFuel == null ? 0 : dataFuel.burnTime();
            int divisor = switch (item.piece.palette.shape) {
                case SLAB, VERTICAL_SLAB -> 2;
                case STEP, HORIZONTAL_STEP, VERTICAL_STEP -> 4;
                case CORNER -> 8;
                default -> 1;
            };
            // A null recipe type is the general fuel query used by recipe viewers.
            for (var furnace : java.util.Arrays.asList(null, RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.BLASTING)) {
                int expected = wooden ? Math.max(0, full.getBurnTime(furnace)) / divisor : defaultFuel;
                int actual = new ItemStack(item).getBurnTime(furnace);
                if (actual != expected) {
                    throw new AssertionError("Piece fuel differs from its material or configured default: "
                            + BuiltInRegistries.ITEM.getKey(item) + " recipe=" + furnace
                            + " expected=" + expected + " actual=" + actual);
                }
                if (wooden) {
                    woodCases++;
                } else {
                    otherCases++;
                }
            }
        }
        if (woodCases == 0 || otherCases == 0) {
            throw new AssertionError("Fuel checks must include both wood and non-wood pieces");
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_FUEL_PASS wood_cases={} nonwood_cases={} general_query=true",
                woodCases, otherCases);
    }

    private static void reportedItems() {
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (String identity : new String[] { "skysbuildingpieces:mineralogy_tuff_corner",
                "skysbuildingpiecesbop:crag_rock_slab" }) {
            var id = net.minecraft.resources.ResourceLocation.parse(identity);
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            try {
                int burnTime = new ItemStack(BuiltInRegistries.ITEM.get(id)).getBurnTime(null);
                org.apache.logging.log4j.LogManager.getLogger().info(
                        "BUILDING_PIECES_REPORTED_FUEL_QUERY item={} burn_time={}", identity, burnTime);
            } catch (IllegalStateException failure) {
                failures.add(identity + ": " + failure.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Reported recipe-viewer fuel queries failed: " + failures);
        }
    }

    private static void configuredNonWoodFuel() {
        Integer expected = Integer.getInteger("skysbuildingpieces.expectedSnowCornerFuel");
        if (expected == null) {
            return;
        }
        var item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(
                "skysbuildingpieces:snow_corner"));
        int actual = new ItemStack(item).getBurnTime(RecipeType.SMELTING);
        if (actual != expected) {
            throw new AssertionError("The non-wood fuel data map was ignored: expected=" + expected
                    + " actual=" + actual);
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_CONFIGURED_FUEL_PASS item=skysbuildingpieces:snow_corner burn_time={}", actual);
    }

    private FuelChecks() {
    }
}

