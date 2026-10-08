package zone.moddev.mc.skysbuildingpieces.content;

import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/** Standalone grass rules, with a lazily linked optional Grass Slabs API bridge. */
public final class GrassLifecycle {
    private static Method sharedGrass, sharedDirt;
    private GrassLifecycle() { }

    public static void initialize() {
        if (!Loader.isModLoaded("skysgrassslabs")) return;
        try {
            Class<?> api = Class.forName("zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi");
            Method register = api.getMethod("registerGrassForm", IBlockState.class, IBlockState.class);
            for (PieceBlock block : Pieces.BLOCKS.values()) if (block.palette.group.equals("grass"))
                for (int orientation = 0; orientation < block.palette.shape.states; ++orientation) {
                    IBlockState grass = Pieces.state("minecraft:grass", block.palette.shape, orientation);
                    IBlockState dirt = Pieces.state("minecraft:dirt", block.palette.shape, orientation);
                    if (grass != null && dirt != null) register.invoke(null, dirt, grass);
                }
            sharedGrass = api.getMethod("tickGrass", World.class, BlockPos.class, IBlockState.class, Random.class);
            sharedDirt = api.getMethod("tickDirt", World.class, BlockPos.class, IBlockState.class, Random.class);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Install Sky's Grass Slabs 1.1.0 or later for grass-piece integration", failure);
        }
    }

    public static boolean grass(IBlockState state) {
        return state.getBlock() instanceof PieceBlock &&
                ((PieceBlock) state.getBlock()).definition(state).type.equals("grass");
    }

    public static IBlockState counterpart(IBlockState state, boolean toGrass) {
        if (!(state.getBlock() instanceof PieceBlock)) return null;
        PieceBlock block = (PieceBlock) state.getBlock();
        if (!block.definition(state).id.equals(toGrass ? "minecraft:dirt" : "minecraft:grass")) return null;
        return Pieces.state(toGrass ? "minecraft:grass" : "minecraft:dirt", block.palette.shape, block.orientation(state));
    }

    public static void repairSupport(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && grass(state) && world.isBlockLoaded(pos.down()) &&
                world.getBlockState(pos.down()).getBlock() == Blocks.GRASS)
            world.setBlockState(pos.down(), Blocks.DIRT.getDefaultState(), 2);
    }

    public static void tick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote) return;
        boolean isGrass = grass(state);
        if (!isGrass && counterpart(state, true) == null) return;
        Method shared = isGrass ? sharedGrass : sharedDirt;
        if (shared != null) {
            try { shared.invoke(null, world, pos, state, random); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Grass integration failed", failure); }
            return;
        }
        if (isGrass) {
            repairSupport(world, pos, state);
            BlockPos above = pos.up();
            if (world.getLightFromNeighbors(above) < 4 && world.getBlockState(above).getLightOpacity(world, above) > 2) {
                world.setBlockState(pos, counterpart(state, false), 3); return;
            }
            if (!world.isAreaLoaded(pos, 3) || world.getLightFromNeighbors(above) < 9 ||
                    world.getBlockState(above).getLightOpacity(world, above) > 2) return;
            for (int attempt = 0; attempt < 4; ++attempt) {
                BlockPos target = pos.add(random.nextInt(3)-1, random.nextInt(5)-3, random.nextInt(3)-1);
                if (!target.equals(pos.down()) && viableTarget(world, target)) {
                    IBlockState dirt = world.getBlockState(target);
                    world.setBlockState(target, dirt.getBlock() == Blocks.DIRT ? Blocks.GRASS.getDefaultState() : counterpart(dirt, true), 3);
                }
            }
        } else if (world.isAreaLoaded(pos, 3) && viableTarget(world, pos)) {
            for (int attempt = 0; attempt < 4; ++attempt) {
                BlockPos source = pos.add(random.nextInt(3)-1, random.nextInt(5)-1, random.nextInt(3)-1);
                if (source.getY() < 0 || source.getY() >= 256 || !world.isBlockLoaded(source)) continue;
                IBlockState origin = world.getBlockState(source), cover = world.getBlockState(source.up());
                if ((origin.getBlock() == Blocks.GRASS || grass(origin)) &&
                        world.getLightFromNeighbors(source.up()) >= 9 && cover.getLightOpacity(world, source.up()) <= 2) {
                    world.setBlockState(pos, counterpart(state, true), 3); return;
                }
            }
        }
    }

    private static boolean viableTarget(World world, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= 255 || !world.isBlockLoaded(pos)) return false;
        IBlockState state = world.getBlockState(pos), cover = world.getBlockState(pos.up());
        boolean dirt = counterpart(state, true) != null || state.getBlock() == Blocks.DIRT &&
                state.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.DIRT;
        return dirt && !grass(cover) && world.getLightFromNeighbors(pos.up()) >= 4 && cover.getLightOpacity(world, pos.up()) <= 2;
    }
}
