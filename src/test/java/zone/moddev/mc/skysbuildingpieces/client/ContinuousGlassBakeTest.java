package zone.moddev.mc.skysbuildingpieces.client;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousGlassBakeTest {
    @Test
    void onlyWallBlockVariantsAreWrappedAndAliasesStayShared() {
        Map<ResourceLocation, IBakedModel> registry = new LinkedHashMap<ResourceLocation, IBakedModel>();
        IBakedModel base = new EmptyModel();
        IBakedModel missing = new EmptyModel();

        ModelResourceLocation wall = key("clear_glass_wall_00", "connections=10,meta=0,up=false");
        ModelResourceLocation alias = key("clear_glass_wall_00", "connections=10,meta=1,up=false");
        ModelResourceLocation item = key("clear_glass_wall_00", "inventory");
        ModelResourceLocation other = key("rock_wall_00", "connections=10,meta=0");

        ModelResourceLocation absent = key("stained_glass_wall_00", "connections=0,meta=0,up=true");

        registry.put(wall, base);
        registry.put(alias, base);
        registry.put(item, base);
        registry.put(other, base);
        registry.put(absent, missing);

        Set<String> targets = new HashSet<String>(
                Arrays.asList("skysbuildingpieces:clear_glass_wall_00", "skysbuildingpieces:stained_glass_wall_00"));

        ContinuousGlassModel.install(registry, missing, targets);

        IBakedModel wrapped = registry.get(wall);

        assertTrue(wrapped instanceof ContinuousGlassModel);
        assertSame(wrapped, registry.get(alias));
        assertSame(base, registry.get(item));
        assertSame(base, registry.get(other));
        assertSame(missing, registry.get(absent));
        ContinuousGlassModel.install(registry, missing, targets);
        assertSame(wrapped, registry.get(wall));
    }

    @Test
    void aResourceReloadUsesTheNewBaseModel() {
        ModelResourceLocation key = key("clear_glass_wall_00", "connections=10,meta=0,up=false");
        Set<String> targets = Collections.singleton("skysbuildingpieces:clear_glass_wall_00");
        Map<ResourceLocation, IBakedModel> registry = new LinkedHashMap<ResourceLocation, IBakedModel>();

        registry.put(key, new EmptyModel());
        ContinuousGlassModel.install(registry, null, targets);

        IBakedModel previous = registry.get(key);

        registry.put(key, new EmptyModel());
        ContinuousGlassModel.install(registry, null, targets);
        assertTrue(registry.get(key) instanceof ContinuousGlassModel);
        assertNotSame(previous, registry.get(key));
    }

    private static ModelResourceLocation key(String block, String variant) {
        return new ModelResourceLocation("skysbuildingpieces:" + block, variant);
    }

    private static final class EmptyModel implements IBakedModel {
        public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
            return Collections.emptyList();
        }

        public boolean isAmbientOcclusion() {
            return false;
        }

        public boolean isGui3d() {
            return true;
        }

        public boolean func_230044_c_() {
            return true;
        }

        public boolean isBuiltInRenderer() {
            return false;
        }

        public TextureAtlasSprite getParticleTexture() {
            return null;
        }

        public ItemCameraTransforms getItemCameraTransforms() {
            return null;
        }

        public ItemOverrideList getOverrides() {
            return ItemOverrideList.EMPTY;
        }
    }
}
