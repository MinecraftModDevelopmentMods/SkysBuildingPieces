package zone.moddev.mc.skysbuildingpieces.client;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.Direction;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Removes repeated glass borders at matching joins without changing the wall geometry. */
public final class ContinuousGlassModel implements IBakedModel {
    private static final ModelProperty<Integer> JOINS = new ModelProperty<>(
            joins -> joins != null && joins >= 0 && joins < 16);
    private final IBakedModel original;
    private final ConcurrentMap<QuadKey, List<BakedQuad>> cache = new ConcurrentHashMap<QuadKey, List<BakedQuad>>();

    public ContinuousGlassModel(IBakedModel original) {
        this.original = original;
    }

    @Override
    public IModelData getModelData(IBlockDisplayReader world, BlockPos position, BlockState state,
            IModelData tileData) {
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

        return new ModelDataMap.Builder().withInitial(JOINS, joins).build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
        return getQuads(state, side, random, EmptyModelData.INSTANCE);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, Random random, IModelData modelData) {
        Integer joins = modelData.getData(JOINS);
        List<BakedQuad> quads = original.getQuads(state, side, random, modelData);

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
            // 1.15 quads use the block vertex format; they no longer carry a format of their own.
            VertexFormat format = DefaultVertexFormats.BLOCK;
            int position = -1;
            int texture = -1;

            for (int elementIndex = 0; elementIndex < format.getElements().size(); elementIndex++) {
                VertexFormatElement element = format.getElements().get(elementIndex);

                if (element.getUsage() == VertexFormatElement.Usage.POSITION
                        && element.getType() == VertexFormatElement.Type.FLOAT) {
                    position = format.getOffset(elementIndex) / 4;
                }
                if (element.getUsage() == VertexFormatElement.Usage.UV
                        && element.getType() == VertexFormatElement.Type.FLOAT && element.getIndex() == 0) {
                    texture = format.getOffset(elementIndex) / 4;
                }
            }
            if (position < 0 || texture < 0) {
                result.add(quad);
                continue;
            }

            int[] data = GlassWallTexture.trim(quad.getVertices(), format.getIntegerSize(), position,
                    texture, joins);

            result.add(data == quad.getVertices() ? quad
                    : new BakedQuad(data, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                            quad.isShade()));
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

    public ItemCameraTransforms getTransforms() {
        return original.getTransforms();
    }

    public ItemOverrideList getOverrides() {
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
        public void bake(ModelBakeEvent event) {
            Set<String> walls = new HashSet<String>();

            for (PieceBlock block : Pieces.BLOCKS.values()) {
                if (block.palette.shape == Shape.WALL && block.palette.group.contains("glass")) {
                    walls.add(block.getRegistryName().toString());
                }
            }

            install(event.getModelRegistry(), event.getModelManager().getMissingModel(), walls);
        }
    }

    static void install(Map<ResourceLocation, IBakedModel> registry, IBakedModel missing, Set<String> walls) {
        IdentityHashMap<IBakedModel, ContinuousGlassModel> wrapped = new IdentityHashMap<IBakedModel, ContinuousGlassModel>();

        for (ResourceLocation key : new ArrayList<>(registry.keySet())) {
            if (!(key instanceof ModelResourceLocation)
                    || ((ModelResourceLocation) key).getVariant().equals("inventory")
                    || !walls.contains(key.getNamespace() + ":" + key.getPath())) {
                continue;
            }

            IBakedModel model = registry.get(key);

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
