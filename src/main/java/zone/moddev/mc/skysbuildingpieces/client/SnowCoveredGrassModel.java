package zone.moddev.mc.skysbuildingpieces.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;

/** Covers the exposed grass below snowy pieces without changing the saved grass block. */
public final class SnowCoveredGrassModel implements BlockStateModel {
    private static final ModelProperty<Boolean> COVERED = new ModelProperty<>();
    private static final BlockState SNOWY_GRASS = Blocks.GRASS_BLOCK.defaultBlockState()
            .setValue(SnowyBlock.SNOWY, true);
    private final BlockStateModel original;
    private final BlockStateModel snowyGrass;
    private final BakedQuad.MaterialInfo snow;
    private final Map<BlockStateModelPart, BlockStateModelPart> tops = new ConcurrentHashMap<>();

    private SnowCoveredGrassModel(BlockStateModel original, BlockStateModel snowyGrass,
            BakedQuad.MaterialInfo snow) {
        this.original = original;
        this.snowyGrass = snowyGrass;
        this.snow = snow;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter world, BlockPos position, BlockState state, ModelData data) {
        ModelData inherited = original.getModelData(world, position, state, data == null ? ModelData.EMPTY : data);
        boolean covered = restsOnGrass(world, position.above());
        if (covered) {
            inherited = snowyGrass.getModelData(world, position, SNOWY_GRASS, inherited);
        }
        return inherited.derive().with(COVERED, covered).build();
    }

    private static boolean restsOnGrass(BlockAndTintGetter world, BlockPos position) {
        BlockState above = world.getBlockState(position);
        if (above.getBlock() instanceof PowderSnowBlock) {
            var outline = above.getShape(world, position);
            return above.getFluidState().isEmpty() && !outline.isEmpty() && outline.bounds().minY == 0;
        }
        if (!(above.getBlock() instanceof PieceBlock piece)
                || above.getValue(BlockStateProperties.WATERLOGGED)
                || piece.palette.shape == Shape.WALL || piece.palette.shape == Shape.PANE) {
            return false;
        }
        boolean snowy = piece.definition(above).type.equals("snow")
                || above.hasProperty(SnowyBlock.SNOWY) && above.getValue(SnowyBlock.SNOWY);
        // An upper half leaves air between the piece and its support.
        return snowy && (piece.mask(above) & 15) != 0;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        original.collectParts(random, parts);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts, ModelData data) {
        data = data == null ? ModelData.EMPTY : data;
        if (!Boolean.TRUE.equals(data.get(COVERED))) {
            original.collectParts(random, parts, data);
            return;
        }
        int start = parts.size();
        snowyGrass.collectParts(random, parts, data);
        for (int index = start; index < parts.size(); index++) {
            BlockStateModelPart part = parts.get(index);
            BlockStateModelPart covered = tops.get(part);
            if (covered == null) {
                covered = tops.size() < 128 ? tops.computeIfAbsent(part, CoveredPart::new) : new CoveredPart(part);
            }
            parts.set(index, covered);
        }
    }

    @Override
    public Material.Baked particleMaterial() {
        return original.particleMaterial();
    }

    @Override
    public Material.Baked particleMaterial(ModelData data) {
        // Terrain particles can ask for a material without block-entity model data.
        data = data == null ? ModelData.EMPTY : data;
        return Boolean.TRUE.equals(data.get(COVERED)) ? snowyGrass.particleMaterial(data) : original.particleMaterial(data);
    }

    @Override
    public int materialFlags() {
        return original.materialFlags() | snowyGrass.materialFlags();
    }

    private final class CoveredPart implements BlockStateModelPart {
        private final BlockStateModelPart original;
        private final Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
        private final List<BakedQuad> unculled;

        CoveredPart(BlockStateModelPart original) {
            this.original = original;
            unculled = cover(original.getQuads(null));
            for (Direction face : Direction.values()) {
                faces.put(face, cover(original.getQuads(face)));
            }
        }

        private List<BakedQuad> cover(List<BakedQuad> quads) {
            return quads.stream().map(quad -> quad.direction() == Direction.UP ? snowTop(quad) : quad).toList();
        }

        @Override
        public List<BakedQuad> getQuads(Direction face) {
            return face == null ? unculled : faces.get(face);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return original.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return original.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return original.materialFlags();
        }
    }

    private BakedQuad snowTop(BakedQuad quad) {
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                snowUv(quad, 0), snowUv(quad, 1), snowUv(quad, 2), snowUv(quad, 3), quad.direction(), snow);
    }

    private long snowUv(BakedQuad quad, int vertex) {
        var previous = quad.materialInfo().sprite();
        var texture = snow.sprite();
        float horizontal = (UVPair.unpackU(quad.packedUV(vertex)) - previous.getU0()) / (previous.getU1() - previous.getU0());
        float vertical = (UVPair.unpackV(quad.packedUV(vertex)) - previous.getV0()) / (previous.getV1() - previous.getV0());
        return UVPair.pack(texture.getU0() + horizontal * (texture.getU1() - texture.getU0()),
                texture.getV0() + vertical * (texture.getV1() - texture.getV0()));
    }

    public static void bake(ModelEvent.ModifyBakingResult event) {
        var models = event.getResults().blockStateModels();
        BlockStateModel snowy = models.get(SNOWY_GRASS);
        BlockStateModel snowBlock = models.get(Blocks.SNOW_BLOCK.defaultBlockState());
        if (snowy == null || snowBlock == null) {
            return;
        }
        List<BlockStateModelPart> parts = new ArrayList<>();
        snowBlock.collectParts(RandomSource.create(0), parts);
        var material = parts.stream().flatMap(part -> part.getQuads(Direction.UP).stream())
                .filter(quad -> quad.direction() == Direction.UP).map(BakedQuad::materialInfo).findFirst();
        if (material.isEmpty()) {
            return;
        }
        for (BlockState state : Blocks.GRASS_BLOCK.getStateDefinition().getPossibleStates()) {
            BlockStateModel original = models.get(state);
            if (original != null && !(original instanceof SnowCoveredGrassModel)) {
                models.put(state, new SnowCoveredGrassModel(original, snowy, material.get()));
            }
        }
    }
}
