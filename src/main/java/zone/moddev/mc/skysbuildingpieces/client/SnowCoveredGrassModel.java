package zone.moddev.mc.skysbuildingpieces.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BakedQuadRetextured;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;


/** A snow cap on exposed grass between partial pieces, including native BOP grass. */
public final class SnowCoveredGrassModel implements IBakedModel {
    private final IBakedModel original;
    private final TextureAtlasSprite snow;
    private final Map<List<BakedQuad>, List<BakedQuad>> tops =
            Collections.synchronizedMap(new IdentityHashMap<List<BakedQuad>, List<BakedQuad>>());

    SnowCoveredGrassModel(IBakedModel original, TextureAtlasSprite snow) {
        this.original = original;
        this.snow = snow;
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long random) {
        List<BakedQuad> quads = original.getQuads(state, side, random);
        if (side != null && side != EnumFacing.UP || quads.isEmpty()) {
            return quads;
        }
        List<BakedQuad> cached = tops.get(quads);
        if (cached != null) {
            return cached;
        }
        List<BakedQuad> covered = new ArrayList<BakedQuad>(quads.size());
        for (BakedQuad quad : quads) {
            if (quad.getFace() == EnumFacing.UP) {
                BakedQuad remapped = new BakedQuadRetextured(quad, snow);
                covered.add(new BakedQuad(remapped.getVertexData(), -1, EnumFacing.UP, snow,
                        quad.shouldApplyDiffuseLighting(), remapped.getFormat()));
            } else {
                covered.add(quad);
            }
        }
        List<BakedQuad> result = Collections.unmodifiableList(covered);
        if (tops.size() < 128) {
            tops.put(quads, result);
        }
        return result;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return original.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return original.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return original.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return original.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return original.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return original.getOverrides();
    }

    static void install(IRegistry<ModelResourceLocation, IBakedModel> registry, IBakedModel missing,
            Set<String> grassBlocks, TextureAtlasSprite snow) {
        Map<IBakedModel, SnowCoveredGrassModel> shared = new IdentityHashMap<IBakedModel, SnowCoveredGrassModel>();
        for (ModelResourceLocation key : new ArrayList<ModelResourceLocation>(registry.getKeys())) {
            if (!grassBlocks.contains(key.getResourceDomain() + ":" + key.getResourcePath())
                    || !Arrays.asList(key.getVariant().split(",")).contains("snowy=true")) {
                continue;
            }
            IBakedModel model = registry.getObject(key);
            if (model == null || model == missing || model instanceof SnowCoveredGrassModel) {
                continue;
            }
            SnowCoveredGrassModel replacement = shared.get(model);
            if (replacement == null) {
                replacement = new SnowCoveredGrassModel(model, snow);
                shared.put(model, replacement);
            }
            registry.putObject(key, replacement);
        }
    }

    public static final class BakeHandler {
        @SubscribeEvent
        public void bake(ModelBakeEvent event) {
            Set<String> grassBlocks = new HashSet<String>();
            for (Block block : Block.REGISTRY) {
                if (block instanceof BlockGrass) {
                    grassBlocks.add(block.getRegistryName().toString());
                }
            }
            install(event.getModelRegistry(), event.getModelManager().getMissingModel(), grassBlocks,
                    Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/snow"));
        }
    }
}
