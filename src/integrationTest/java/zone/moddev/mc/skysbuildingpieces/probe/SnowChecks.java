package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Checks real calculated states, including the native grass hook and render chunk cache. */
final class SnowChecks {
    static void run(WorldServer world) throws ReflectiveOperationException {
        BlockPos support = new BlockPos(1500, 220, 1500);
        BlockPos position = support.up();
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                world.getChunk((support.getX() >> 4) + chunkX, (support.getZ() >> 4) + chunkZ);
            }
        }
        world.setBlockState(position.up(3), Blocks.GLASS.getDefaultState(), 2);
        world.setBlockState(support, Blocks.GRASS.getDefaultState(), 2);
        int checked = 0;
        for (PieceBlock piece : Pieces.BLOCKS.values()) {
            if (!piece.palette.group.equals("snow") && !piece.palette.group.equals("grass")
                    && !piece.palette.group.equals("dirt")) {
                continue;
            }
            for (int orientation = 0; orientation < piece.palette.shape.states; orientation++) {
                IBlockState state = state(piece, orientation);
                world.setBlockState(position, state, 2);
                boolean touches = piece.palette.shape != Shape.WALL && piece.palette.shape != Shape.PANE
                        && (piece.mask(state) & 15) != 0;
                boolean physicalSnow = piece.palette.group.equals("snow");
                require(snowy(world, support) == (physicalSnow && touches),
                        "physical snow support " + piece.palette.shape + "/" + orientation);
                if (!physicalSnow) {
                    world.setBlockState(position.east(), Blocks.SNOW.getDefaultState(), 2);
                    require(actual(piece, state, world, position).getValue(BlockGrass.SNOWY),
                            "adjacent physical snow reaches grass/dirt piece");
                    require(snowy(world, support) == touches, "calculated cap reaches touching support");
                    world.setBlockToAir(position.east());
                    require(!actual(piece, state, world, position).getValue(BlockGrass.SNOWY),
                            "removed snow clears the piece");
                }
                require(world.getBlockState(position).equals(state), "snow appearance preserves saved metadata");
                require(!world.getBlockState(support).getValue(BlockGrass.SNOWY), "support cap is not saved");
                world.setBlockToAir(position);
                require(!snowy(world, support), "removed cover clears the support");
                checked++;
            }
        }
        IBlockState grass = Pieces.state("minecraft:grass", Shape.HORIZONTAL_STEP, 0);
        Block block = grass.getBlock();
        world.setBlockState(position, grass, 2);
        for (EnumFacing direction : EnumFacing.HORIZONTALS) {
            BlockPos higher = position.offset(direction);
            world.setBlockState(higher, Blocks.GRASS.getDefaultState(), 2);
            world.setBlockState(higher.up(), Blocks.SNOW_LAYER.getDefaultState(), 2);
            require(actual(block, grass, world, position).getValue(BlockGrass.SNOWY), "upper terrace " + direction);
            world.setBlockToAir(higher.up());
            world.setBlockToAir(higher);
            require(!actual(block, grass, world, position).getValue(BlockGrass.SNOWY), "upper snow removal");
        }
        byte[] biomes = world.getChunk(position).getBiomeArray();
        int biomeIndex = (position.getZ() & 15) << 4 | position.getX() & 15;
        byte originalBiome = biomes[biomeIndex];
        try {
            biomes[biomeIndex] = (byte) net.minecraft.world.biome.Biome.getIdForBiome(Biomes.ICE_PLAINS);
            world.setBlockToAir(position.up(3));
            require(actual(block, grass, world, position).getValue(BlockGrass.SNOWY), "exposed cold piece");
            ChunkCache cache = new ChunkCache(world, position.add(-2, -2, -2), position.add(2, 4, 2), 0);
            require(block.getActualState(grass, cache, position).getValue(BlockGrass.SNOWY), "render cache uses its world");
            world.setBlockState(position.up(3), Blocks.GLASS.getDefaultState(), 2);
            require(!actual(block, grass, world, position).getValue(BlockGrass.SNOWY), "glass roof blocks weather cap");
        } finally {
            biomes[biomeIndex] = originalBiome;
            world.setBlockToAir(position.up(3));
            world.setBlockToAir(position);
            world.setBlockToAir(support);
        }
        BlockPos edge = new BlockPos(5007, 220, 5007);
        world.getChunk(edge);
        world.setBlockState(edge, Blocks.GRASS.getDefaultState(), 2);
        world.setBlockState(edge.up(), grass, 2);
        // Placement may populate adjacent chunks. Unload the eastern one before reading visuals.
        net.minecraft.world.chunk.Chunk neighbour = world.getChunkProvider().getLoadedChunk(
                (edge.getX() >> 4) + 1, edge.getZ() >> 4);
        if (neighbour != null) {
            world.getChunkProvider().queueUnload(neighbour);
            world.getChunkProvider().tick();
        }
        int loaded = world.getChunkProvider().getLoadedChunkCount();
        boolean unavailable = false;
        for (EnumFacing direction : EnumFacing.HORIZONTALS) {
            unavailable |= !world.isBlockLoaded(edge.offset(direction));
        }
        require(unavailable, "the snow border test has an unloaded neighbour");
        snowy(world, edge);
        require(world.getChunkProvider().getLoadedChunkCount() == loaded, "snow visuals do not load a neighbouring chunk");
        world.setBlockToAir(edge.up());
        world.setBlockToAir(edge);
        System.out.println("BUILDING_PIECES_SNOW_PASS orientations=" + checked
                + " metadata=true removal=true cache=true loaded_halo=true");
    }

    private static IBlockState state(Block block, int metadata) {
        return block.getStateFromMeta(metadata);
    }

    private static IBlockState actual(Block block, IBlockState state, WorldServer world, BlockPos position) {
        return block.getActualState(state, world, position);
    }

    private static boolean snowy(WorldServer world, BlockPos position) {
        return actual(Blocks.GRASS, world.getBlockState(position), world, position).getValue(BlockGrass.SNOWY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private SnowChecks() {
    }
}
