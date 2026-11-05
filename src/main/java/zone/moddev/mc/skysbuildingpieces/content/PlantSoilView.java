package zone.moddev.mc.skysbuildingpieces.content;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.TriState;

/** Shows a plant the full soil beneath it while keeping its real surroundings. */
record PlantSoilView(LevelReader world, BlockPos soilPosition, BlockState soil) implements LevelReader {
    static TriState supports(BlockState soil, BlockGetter world, BlockPos position, Direction face,
            BlockState plant) {
        TriState support = soil.canSustainPlant(world, position, face, plant);
        if (!support.isDefault() || !(world instanceof LevelReader level)) {
            return support;
        }
        // Ask the plant about the full soil without changing the actual block.
        return plant.canSurvive(new PlantSoilView(level, position, soil), position.relative(face))
                ? TriState.TRUE : TriState.FALSE;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return soilPosition.equals(position) ? soil : world.getBlockState(position);
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return soilPosition.equals(position) ? soil.getFluidState() : world.getFluidState(position);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return soilPosition.equals(position) ? null : world.getBlockEntity(position);
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
        return world.getChunk(x, z, status, load);
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return world.hasChunk(x, z);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return world.getHeight(type, x, z);
    }

    @Override
    public int getSkyDarken() {
        return world.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return world.getBiomeManager();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return world.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public boolean isClientSide() {
        return world.isClientSide();
    }

    @Override
    public int getSeaLevel() {
        return world.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return world.dimensionType();
    }

    @Override
    public RegistryAccess registryAccess() {
        return world.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return world.enabledFeatures();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return world.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return world.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos position, ColorResolver resolver) {
        return world.getBlockTint(position, resolver);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return world.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int x, int z) {
        return world.getChunkForCollisions(x, z);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(Entity entity, AABB area) {
        return world.getEntityCollisions(entity, area);
    }
}
