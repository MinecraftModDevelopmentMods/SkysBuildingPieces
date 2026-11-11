package zone.moddev.mc.skysbuildingpieces.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;

/** Snow footprints cover the exposed grass top without adding a layer or changing the saved block. */
public final class SnowCoveredGrassModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> COVERED = new ModelProperty<>();
    private static final BlockState SNOWY_GRASS = Blocks.GRASS_BLOCK.defaultBlockState()
            .setValue(SnowyDirtBlock.SNOWY, true);
    private final BakedModel snowyGrass;
    private final TextureAtlasSprite snowTexture;
    private final Map<List<BakedQuad>, List<BakedQuad>> tops = new ConcurrentHashMap<>();

    private SnowCoveredGrassModel(BakedModel original, BakedModel snowyGrass, TextureAtlasSprite snowTexture) {
        super(original);
        this.snowyGrass = snowyGrass;
        this.snowTexture = snowTexture;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter world, BlockPos position, BlockState state, ModelData data) {
        ModelData inherited = originalModel.getModelData(world, position, state, data);
        boolean covered = restsOnGrass(world.getBlockState(position.above()));
        if (covered) {
            inherited = snowyGrass.getModelData(world, position, SNOWY_GRASS, inherited);
        }
        return inherited.derive().with(COVERED, covered).build();
    }

    private static boolean restsOnGrass(BlockState above) {
        if (!(above.getBlock() instanceof PieceBlock piece)
                || above.getValue(BlockStateProperties.WATERLOGGED)
                || piece.palette.shape == Shape.WALL || piece.palette.shape == Shape.PANE) {
            return false;
        }
        boolean snowy = piece.definition(above).type.equals("snow")
                || above.hasProperty(SnowyDirtBlock.SNOWY) && above.getValue(SnowyDirtBlock.SNOWY);

        // Upper halves leave a gap above the ground. Only a footprint touching
        // the lower face can cover the grass directly underneath it.
        return snowy && (piece.mask(above) & 15) != 0;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random,
            ModelData data, RenderType layer) {
        if (state == null || !Boolean.TRUE.equals(data.get(COVERED))) {
            return originalModel.getQuads(state, face, random, data, layer);
        }
        List<BakedQuad> quads = snowyGrass.getQuads(SNOWY_GRASS, face, random, data, layer);
        if (quads.stream().noneMatch(quad -> quad.getDirection() == Direction.UP)) {
            return quads;
        }
        List<BakedQuad> cached = tops.get(quads);
        if (cached != null) {
            return cached;
        }
        List<BakedQuad> covered = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            covered.add(quad.getDirection() == Direction.UP ? snowTop(quad) : quad);
        }
        covered = List.copyOf(covered);
        if (tops.size() < 128) {
            tops.putIfAbsent(quads, covered);
        }
        return covered;
    }

    private BakedQuad snowTop(BakedQuad quad) {
        int[] vertices = quad.getVertices().clone();
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
        int textureOffset = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV0) / Integer.BYTES;
        TextureAtlasSprite previous = quad.getSprite();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride + textureOffset;
            float horizontal = (Float.intBitsToFloat(vertices[offset]) - previous.getU0())
                    / (previous.getU1() - previous.getU0());
            float vertical = (Float.intBitsToFloat(vertices[offset + 1]) - previous.getV0())
                    / (previous.getV1() - previous.getV0());
            vertices[offset] = Float.floatToRawIntBits(snowTexture.getU0()
                    + horizontal * (snowTexture.getU1() - snowTexture.getU0()));
            vertices[offset + 1] = Float.floatToRawIntBits(snowTexture.getV0()
                    + vertical * (snowTexture.getV1() - snowTexture.getV0()));
        }
        // Keep the original face and lighting, but snow must not inherit grass tint.
        return new BakedQuad(vertices, -1, quad.getDirection(), snowTexture,
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return Boolean.TRUE.equals(data.get(COVERED))
                ? snowyGrass.getRenderTypes(SNOWY_GRASS, random, data)
                : originalModel.getRenderTypes(state, random, data);
    }

    public static void bake(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        BakedModel snowy = models.get(BlockModelShaper.stateToModelLocation(SNOWY_GRASS));
        TextureAtlasSprite texture = event.getTextureGetter().apply(new Material(
                TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/snow")));
        for (BlockState state : Blocks.GRASS_BLOCK.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation identity = BlockModelShaper.stateToModelLocation(state);
            BakedModel original = models.get(identity);
            if (original != null && snowy != null && !(original instanceof SnowCoveredGrassModel)) {
                models.put(identity, new SnowCoveredGrassModel(original, snowy, texture));
            }
        }
    }
}
