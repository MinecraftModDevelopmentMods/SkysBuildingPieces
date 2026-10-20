package zone.moddev.mc.skysbuildingpieces.client;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Removes repeated glass borders at matching joins without changing the wall geometry. */
public final class ContinuousGlassModel implements IBakedModel {
    private final IBakedModel original;
    private final ConcurrentMap<QuadKey, List<BakedQuad>> cache = new ConcurrentHashMap<QuadKey, List<BakedQuad>>();

    public ContinuousGlassModel(IBakedModel original) {
        this.original = original;
    }

    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long random) {
        Integer joins = state instanceof IExtendedBlockState
                ? ((IExtendedBlockState) state).getValue(GlassWallTexture.JOINS)
                : null;
        IBlockState clean = state instanceof IExtendedBlockState ? ((IExtendedBlockState) state).getClean() : state;
        List<BakedQuad> quads = original.getQuads(clean, side, random);

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
            VertexFormat format = quad.getFormat();
            int position = -1;

            for (int elementIndex = 0; elementIndex < format.getElementCount(); elementIndex++) {
                VertexFormatElement element = format.getElement(elementIndex);

                if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION
                        && element.getType() == VertexFormatElement.EnumType.FLOAT) {
                    position = format.getOffset(elementIndex) / 4;
                }
            }
            if (position < 0 || !format.hasUvOffset(0)) {
                result.add(quad);
                continue;
            }

            int[] data = GlassWallTexture.trim(quad.getVertexData(), format.getIntegerSize(), position,
                    format.getUvOffsetById(0) / 4, joins);

            result.add(data == quad.getVertexData() ? quad
                    : new BakedQuad(data, quad.getTintIndex(), quad.getFace(), quad.getSprite(),
                            quad.shouldApplyDiffuseLighting(), format));
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

    public boolean isAmbientOcclusion() {
        return original.isAmbientOcclusion();
    }

    public boolean isGui3d() {
        return original.isGui3d();
    }

    public boolean isBuiltInRenderer() {
        return original.isBuiltInRenderer();
    }

    public TextureAtlasSprite getParticleTexture() {
        return original.getParticleTexture();
    }

    public ItemCameraTransforms getItemCameraTransforms() {
        return original.getItemCameraTransforms();
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
        @SubscribeEvent
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

    static void install(net.minecraft.util.registry.IRegistry<ModelResourceLocation, IBakedModel> registry,
            IBakedModel missing, Set<String> walls) {
        IdentityHashMap<IBakedModel, ContinuousGlassModel> wrapped = new IdentityHashMap<IBakedModel, ContinuousGlassModel>();

        for (ModelResourceLocation key : new ArrayList<ModelResourceLocation>(registry.getKeys())) {
            if (key.getVariant().equals("inventory")
                    || !walls.contains(key.getResourceDomain() + ":" + key.getResourcePath())) {
                continue;
            }

            IBakedModel model = registry.getObject(key);

            if (model == null || model == missing || model instanceof ContinuousGlassModel) {
                continue;
            }

            ContinuousGlassModel replacement = wrapped.get(model);

            if (replacement == null) {
                replacement = new ContinuousGlassModel(model);
                wrapped.put(model, replacement);
            }

            registry.putObject(key, replacement);
        }
    }
}
