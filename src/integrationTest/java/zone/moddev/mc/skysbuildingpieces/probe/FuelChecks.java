package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceItem;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Check the native furnace result, not Forge's -1 default-fuel sentinel. */
public final class FuelChecks {
    private static final Method BURN_DURATION = burnDurationMethod();

    public static void run(ServerLevel world) {
        BlockPos position = new BlockPos(8, world.getMaxY() - 8, 8);
        BlockState original = world.getBlockState(position);
        int pieces = 0;
        int cases = 0;
        int bopWoodCases = 0;
        int hellbarkCases = 0;
        boolean bopLoaded = net.minecraftforge.fml.ModList.isLoaded("skysbuildingpiecesbop");

        try {
            for (var furnaceBlock : java.util.List.of(Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE)) {
                clear(world, position);
                world.setBlock(position, furnaceBlock.defaultBlockState(), 3);
                var furnace = (AbstractFurnaceBlockEntity) world.getBlockEntity(position);
                int plankFuel = duration(furnace, world, new ItemStack(Items.ACACIA_PLANKS));
                require(plankFuel > 0, "The native acacia-plank fuel control must be positive");
                if (furnaceBlock == Blocks.FURNACE) {
                    require(plankFuel == Integer.getInteger("skysbuildingpieces.expectedWoodBurnTime", 300),
                            "The native plank control has an unexpected fuel value: " + plankFuel);
                }

                var consumed = new ArrayList<PieceItem>();
                PieceItem nonWoodControl = null;
                for (var item : BuiltInRegistries.ITEM) {
                    if (!(item instanceof PieceItem piece)) {
                        continue;
                    }
                    String id = BuiltInRegistries.ITEM.getKey(item).toString();
                    var definition = Catalogue.INSTANCE.materials.get(piece.material);
                    var stack = new ItemStack(item);
                    int actual = duration(furnace, world, stack);
                    int expected = 0;
                    if (piece.material.equals("minecraft:obsidian") && piece.piece.palette.shape == Shape.CORNER) {
                        nonWoodControl = piece;
                    }
                    if (definition.type.equals("wood")) {
                        ItemStack full = Pieces.stack(Pieces.nativeState(definition, "full"), 1);
                        require(full != null && !full.isEmpty(), "No native full-block fuel control for " + id);
                        int nativeFuel = duration(furnace, world, full);
                        expected = nativeFuel / divisor(piece.piece.palette.shape);
                        require(stack.has(DataComponents.COOKING_FUEL) == (nativeFuel > 0),
                                "Cooking-fuel component does not match the native material: " + id);
                        if (id.startsWith("skysbuildingpiecesbop:")) {
                            bopWoodCases++;
                            if (piece.material.equals("biomesoplenty:planks_hellbark")) {
                                require(nativeFuel == 0, "The native hellbark control is no longer non-fuel wood");
                                hellbarkCases++;
                            }
                        }
                        if (piece.piece.palette.shape == Shape.CORNER && nativeFuel > 0
                                && (piece.material.equals("minecraft:planks_acacia")
                                || piece.material.equals("biomesoplenty:planks_fir"))) {
                            consumed.add(piece);
                            org.apache.logging.log4j.LogManager.getLogger().info(
                                    "BUILDING_PIECES_FUEL_CONTROL material={} furnace={} full={} corner={}",
                                    piece.material, furnaceBlock, nativeFuel, actual);
                        }
                    }
                    require(actual >= 0 && actual == expected,
                            "Wrong furnace fuel for " + id + " in " + furnaceBlock
                                    + ": expected=" + expected + " actual=" + actual);
                    // Forge permits -1 here. A recipe viewer may ask before choosing a furnace.
                    for (RecipeType<?> type : Arrays.<RecipeType<?>>asList(null, RecipeType.SMELTING,
                            RecipeType.SMOKING, RecipeType.BLASTING)) {
                        require(stack.getBurnTime(type) >= -1, "Invalid Forge fuel sentinel for " + id);
                    }
                    cases++;
                    if (furnaceBlock == Blocks.FURNACE) {
                        pieces++;
                    }
                }
                require(!consumed.isEmpty(), "The positive acacia-corner consumption control is missing");
                for (var piece : consumed) {
                    consume(world, position, furnaceBlock.defaultBlockState(), new ItemStack(piece, 2));
                }

                require(nonWoodControl != null, "The non-wood fuel-component control is missing");
                ItemStack override = new ItemStack(nonWoodControl);
                override.set(DataComponents.COOKING_FUEL,
                        new CookingFuel(new ResolvableInt.Constant(13), new ResolvableFloat.Constant(1)));
                require(duration(furnace, world, override) == 13, "An explicit fuel component was ignored");
                ItemStack removed = new ItemStack(consumed.get(0));
                removed.remove(DataComponents.COOKING_FUEL);
                require(duration(furnace, world, removed) == 0, "Removing a wooden fuel component was ignored");
            }
            require(pieces > 0, "No registered pieces were checked");
            require(!bopLoaded || bopWoodCases > 0 && hellbarkCases > 0,
                    "The BOP fuel catalogue or hellbark negative controls were skipped");
            org.apache.logging.log4j.LogManager.getLogger().info(
                    "BUILDING_PIECES_FUEL_PASS pieces={} cases={} bop_wood={} hellbark={} native_furnaces=true",
                    pieces, cases, bopWoodCases, hellbarkCases);
        } finally {
            clear(world, position);
            world.setBlock(position, original, 3);
        }
    }

    private static int divisor(Shape shape) {
        return switch (shape) {
            case SLAB, VERTICAL_SLAB -> 2;
            case STEP, HORIZONTAL_STEP, VERTICAL_STEP -> 4;
            case CORNER -> 8;
            default -> 1;
        };
    }

    private static void consume(ServerLevel world, BlockPos position, BlockState state, ItemStack fuel) {
        clear(world, position);
        world.removeBlock(position, false);
        world.setBlock(position, state, 3);
        var furnace = (AbstractFurnaceBlockEntity) world.getBlockEntity(position);
        var input = state.is(Blocks.SMOKER) ? Items.PORKCHOP
                : state.is(Blocks.BLAST_FURNACE) ? Items.RAW_IRON : Items.COBBLESTONE;
        furnace.setItem(0, new ItemStack(input));
        furnace.setItem(1, fuel.copy());
        int expected = duration(furnace, world, fuel);
        require(expected > 0, "A wooden corner cannot start its furnace");
        AbstractFurnaceBlockEntity.serverTick(world, position, state, furnace);
        require(furnace.getItem(1).getCount() == fuel.getCount() - 1,
                "Starting the furnace must consume exactly one wooden piece");
        var saved = furnace.saveWithoutMetadata(world.registryAccess());
        require(saved.getIntOr("lit_total_time", 0) == expected,
                "The running furnace did not retain the checked burn duration");
        AbstractFurnaceBlockEntity.serverTick(world, position, furnace.getBlockState(), furnace);
        require(furnace.getItem(1).getCount() == fuel.getCount() - 1,
                "The second furnace tick consumed another piece");
    }

    private static void clear(ServerLevel world, BlockPos position) {
        if (world.getBlockEntity(position) instanceof net.minecraft.world.Container container) {
            container.clearContent();
        }
    }

    private static int duration(AbstractFurnaceBlockEntity furnace, ServerLevel world, ItemStack stack) {
        try {
            return (int) BURN_DURATION.invoke(furnace, world, stack);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not exercise Forge's native furnace fuel calculation", failure);
        }
    }

    private static Method burnDurationMethod() {
        try {
            Method method = AbstractFurnaceBlockEntity.class.getDeclaredMethod(
                    "getBurnDuration", ServerLevel.class, ItemStack.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private FuelChecks() {
    }
}
