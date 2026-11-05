package zone.moddev.mc.skysbuildingpieces.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

import net.neoforged.neoforge.client.model.data.ModelData;

import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.bus.api.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Removes repeated glass borders at matching joins without changing the wall geometry. */
public final class ContinuousGlassModel implements BakedModel {
    private static final ModelProperty<Integer> JOINS = new ModelProperty<>(
            joins -> joins != null && joins >= 0 && joins < 16);
    private final BakedModel original;
    private final ConcurrentMap<QuadKey, List<BakedQuad>> cache = new ConcurrentHashMap<QuadKey, List<BakedQuad>>();

    public ContinuousGlassModel(BakedModel original) {
        this.original = original;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter world, BlockPos position, BlockState state,
            ModelData tileData) {
        int joins = 0;
        PieceBlock wall = (PieceBlock) state.getBlock();
        int connectionBit = 1;

        for (Direction direction : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                Direction.WEST }) {
            BlockState neighbour = world.getBlockState(position.relative(direction));

            if (neighbour.getBlock() instanceof PieceBlock) {
                PieceBlock other = (PieceBlock) neighbour.getBlock();

                if (other.palette.shape == Shape.WALL
                        && other.definition(neighbour).id.equals(wall.definition(state).id)) {
                    joins |= connectionBit;
                }
            }

            connectionBit <<= 1;
        }

        return tileData.derive().with(JOINS, joins).build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData modelData, RenderType renderType) {
        Integer joins = modelData.get(JOINS);
        List<BakedQuad> quads = original.getQuads(state, side, random, modelData, renderType);

        if (joins == null || joins == 0 || quads.isEmpty()) {
            return quads;
        }

        QuadKey key = new QuadKey(quads, joins);
        List<BakedQuad> saved = cache.get(key);

        if (saved != null) {
            return saved;
        }

        List<BakedQuad> result = new ArrayList<BakedQuad>(quads.size());

        for (BakedQuad quad : quads) {
            // Baked block quads use the shared vertex layout.
            VertexFormat format = DefaultVertexFormat.BLOCK;
            int positionOffset = format.getOffset(VertexFormatElement.POSITION);
            int textureOffset = format.getOffset(VertexFormatElement.UV0);
            if (positionOffset < 0 || textureOffset < 0) {
                result.add(quad);
                continue;
            }

            int[] data = GlassWallTexture.trim(quad.getVertices(), format.getVertexSize() / Integer.BYTES,
                    positionOffset / Integer.BYTES, textureOffset / Integer.BYTES, joins);

            result.add(data == quad.getVertices() ? quad
                    : new BakedQuad(data, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                            quad.isShade(), quad.hasAmbientOcclusion()));
        }

        List<BakedQuad> immutable = Collections.unmodifiableList(result);

        // Static wall models reuse their lists. Bound the cache for replacement models.
        if (cache.size() < 128) {
            List<BakedQuad> previous = cache.putIfAbsent(key, immutable);

            if (previous != null) {
                return previous;
            }
        }

        return immutable;
    }

    @Override
    public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return original.getRenderTypes(state, random, data);
    }

    @Override
    public List<RenderType> getRenderTypes(net.minecraft.world.item.ItemStack stack, boolean fabulous) {
        return original.getRenderTypes(stack, fabulous);
    }

    public boolean useAmbientOcclusion() {
        return original.useAmbientOcclusion();
    }

    public boolean isGui3d() {
        return original.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return original.usesBlockLight();
    }

    public boolean isCustomRenderer() {
        return original.isCustomRenderer();
    }

    public TextureAtlasSprite getParticleIcon() {
        return original.getParticleIcon();
    }

    public ItemTransforms getTransforms() {
        return original.getTransforms();
    }

    public ItemOverrides getOverrides() {
        return original.getOverrides();
    }

    private static final class QuadKey {
        private final List<BakedQuad> quads;
        private final int joins;

        QuadKey(List<BakedQuad> quads, int joins) {
            this.quads = quads;
            this.joins = joins;
        }

        @Override
        public int hashCode() {
            return quads.hashCode() * 31 + joins;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof QuadKey && ((QuadKey) other).joins == joins
                    && ((QuadKey) other).quads.equals(quads);
        }
    }

    public static final class BakeHandler {
        public void bake(ModelEvent.ModifyBakingResult event) {
            Set<String> walls = new HashSet<String>();

            for (PieceBlock block : Pieces.BLOCKS.values()) {
                if (block.palette.shape == Shape.WALL && block.palette.group.contains("glass")) {
                    walls.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }

            install(event.getModels(), event.getModels().get(ModelBakery.MISSING_MODEL_LOCATION), walls);
        }
    }

    static void install(Map<ModelResourceLocation, BakedModel> registry, BakedModel missing, Set<String> walls) {
        IdentityHashMap<BakedModel, ContinuousGlassModel> wrapped = new IdentityHashMap<BakedModel, ContinuousGlassModel>();

        for (ModelResourceLocation key : new ArrayList<>(registry.keySet())) {
            if (key.variant().equals("inventory") || !walls.contains(key.id().toString())) {
                continue;
            }

            BakedModel model = registry.get(key);

            if (model == null || model == missing || model instanceof ContinuousGlassModel) {
                continue;
            }

            ContinuousGlassModel replacement = wrapped.get(model);

            if (replacement == null) {
                replacement = new ContinuousGlassModel(model);
                wrapped.put(model, replacement);
            }

            registry.put(key, replacement);
        }
    }
}
