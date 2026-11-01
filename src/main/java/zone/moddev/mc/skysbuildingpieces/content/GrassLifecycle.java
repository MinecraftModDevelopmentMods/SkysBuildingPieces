package zone.moddev.mc.skysbuildingpieces.content;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.ModList;

/** Standalone grass rules with an optional shared Grass Slabs lifecycle. */
public final class GrassLifecycle {
    private static Method sharedGrass;
    private static Method sharedDirt;

    private GrassLifecycle() {
    }

    public static void initialize() {
        if (!ModList.get().isLoaded("skysgrassslabs")) {
            return;
        }
        try {
            Class<?> api = Class.forName("zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi");
            Method register = api.getMethod("registerGrassForm", BlockState.class, BlockState.class);
            Set<BlockState> registered = new HashSet<>();

            for (PieceBlock block : Pieces.BLOCKS.values()) {
                for (BlockState state : block.getStateContainer().getValidStates()) {
                    if (!grass(state) || !block.definition(state).id.equals("minecraft:grass")) {
                        continue;
                    }

                    BlockState canonical = state.with(net.minecraft.block.SnowyDirtBlock.SNOWY, false);
                    BlockState dirt = counterpart(canonical, false);

                    if (dirt != null && registered.add(canonical)) {
                        register.invoke(null, dirt, canonical);
                    }
                }
            }

            sharedGrass = api.getMethod("tickGrass", World.class, BlockPos.class, BlockState.class, Random.class);
            sharedDirt = api.getMethod("tickDirt", World.class, BlockPos.class, BlockState.class, Random.class);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Install Sky's Grass Slabs 1.2.0.115021 or later for grass-piece integration", failure);
        }
    }

    public static boolean grass(BlockState state) {
        return state.getBlock() instanceof PieceBlock
                && ((PieceBlock) state.getBlock()).definition(state).type.equals("grass");
    }

    /** Retain orientation, water and joined stair shape when the material changes. */
    public static BlockState counterpart(BlockState state, boolean toGrass) {
        if (!(state.getBlock() instanceof PieceBlock)) {
            return null;
        }

        PieceBlock block = (PieceBlock) state.getBlock();

        if (!block.definition(state).id.equals(toGrass ? "minecraft:dirt" : "minecraft:grass")) {
            return null;
        }

        BlockState replacement = Pieces.state(toGrass ? "minecraft:grass" : "minecraft:dirt", block.palette.shape,
                block.orientation(state));

        if (replacement != null && replacement.has(SlabBlock.WATERLOGGED)) {
            replacement = replacement.with(SlabBlock.WATERLOGGED, state.get(SlabBlock.WATERLOGGED));
        }
        if (replacement != null && replacement.has(StairsBlock.SHAPE)) {
            replacement = replacement.with(StairsBlock.SHAPE, state.get(StairsBlock.SHAPE));
        }

        return replacement;
    }

    public static void repairSupport(World world, BlockPos position, BlockState state) {
        if (!world.isRemote && grass(state) && coversSupport(state) && world.isBlockPresent(position.down())
                && world.getBlockState(position.down()).getBlock() == Blocks.GRASS_BLOCK) {
            world.setBlockState(position.down(), Blocks.DIRT.getDefaultState(), 2);
        }
    }

    public static boolean coversSupport(BlockState state) {
        PieceBlock block = (PieceBlock) state.getBlock();
        int occupied = block.mask(state);

        return ((occupied & 15) | (occupied >>> 4 & 15)) == 15;
    }

    public static void tick(World world, BlockPos position, BlockState state, Random random) {
        if (world.isRemote || !(state.getBlock() instanceof PieceBlock)) {
            return;
        }

        Method shared = grass(state) ? sharedGrass : sharedDirt;

        if (shared != null) {
            try {
                shared.invoke(null, world, position, state, random);
                return;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Grass-piece integration failed", failure);
            }
        }
        if (grass(state)) {
            repairSupport(world, position, state);

            if (state.get(SlabBlock.WATERLOGGED) || !canRemainGrass(world, position)) {
                BlockState dirt = counterpart(state, false);

                if (dirt != null) {
                    world.setBlockState(position, dirt, 3);
                }

                return;
            }
            if (!world.isAreaLoaded(position, 3) || world.getLight(position.up()) < 9) {
                return;
            }
            for (int attempt = 0; attempt < 4; attempt++) {
                BlockPos target = position.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);

                if ((target.equals(position.down()) && coversSupport(state)) || !viableTarget(world, target)) {
                    continue;
                }

                BlockState existing = world.getBlockState(target);
                BlockState grown = existing.getBlock() == Blocks.DIRT ? Blocks.GRASS_BLOCK.getDefaultState()
                        : counterpart(existing, true);

                if (grown != null) {
                    world.setBlockState(target, grown, 3);
                }
            }
        } else if (counterpart(state, true) != null && viableTarget(world, position)
                && world.isAreaLoaded(position, 3)) {
            for (int attempt = 0; attempt < 4; attempt++) {
                BlockPos source = position.add(random.nextInt(3) - 1, random.nextInt(5) - 1, random.nextInt(3) - 1);
                BlockState neighbour = world.getBlockState(source);

                if ((neighbour.getBlock() == Blocks.GRASS_BLOCK || grass(neighbour))
                        && neighbour.getFluidState().isEmpty() && canRemainGrass(world, source)) {
                    world.setBlockState(position, counterpart(state, true), 3);
                    break;
                }
            }
        }
    }

    private static boolean canRemainGrass(World world, BlockPos position) {
        BlockPos above = position.up();

        return world.getLight(above) >= 4
                || world.getBlockState(above).getOpacity(world, above) < world.getMaxLightLevel();
    }

    private static boolean viableTarget(World world, BlockPos position) {
        if (position.getY() < 0 || position.getY() >= 256 || !world.isBlockPresent(position)) {
            return false;
        }

        BlockState state = world.getBlockState(position);

        if (state.getBlock() != Blocks.DIRT && counterpart(state, true) == null) {
            return false;
        }
        if (!state.getFluidState().isEmpty() || state.has(SlabBlock.WATERLOGGED) && state.get(SlabBlock.WATERLOGGED)) {
            return false;
        }

        BlockPos above = position.up();
        BlockState cover = world.getBlockState(above);

        if (grass(cover) && (state.getBlock() != Blocks.DIRT || coversSupport(cover))) {
            return false;
        }

        return world.getLight(above) >= 4 && cover.getOpacity(world, above) < world.getMaxLightLevel()
                && world.getFluidState(above).isEmpty();
    }
}
