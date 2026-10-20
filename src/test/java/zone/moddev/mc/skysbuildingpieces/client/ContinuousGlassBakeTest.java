package zone.moddev.mc.skysbuildingpieces.client;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.registry.RegistrySimple;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousGlassBakeTest {
    @Test
    void onlyWallBlockVariantsAreWrappedAndAliasesStayShared() {
        RegistrySimple<ModelResourceLocation, IBakedModel> registry = new RegistrySimple<ModelResourceLocation, IBakedModel>();
        IBakedModel base = new EmptyModel();
        IBakedModel missing = new EmptyModel();

        ModelResourceLocation wall = key("clear_glass_wall_00", "connections=10,meta=0,up=false");
        ModelResourceLocation alias = key("clear_glass_wall_00", "connections=10,meta=1,up=false");
        ModelResourceLocation item = key("clear_glass_wall_00", "inventory");
        ModelResourceLocation other = key("rock_wall_00", "connections=10,meta=0");

        ModelResourceLocation absent = key("stained_glass_wall_00", "connections=0,meta=0,up=true");

        registry.putObject(wall, base);
        registry.putObject(alias, base);
        registry.putObject(item, base);
        registry.putObject(other, base);
        registry.putObject(absent, missing);

        Set<String> targets = new HashSet<String>(
                Arrays.asList("skysbuildingpieces:clear_glass_wall_00", "skysbuildingpieces:stained_glass_wall_00"));

        ContinuousGlassModel.install(registry, missing, targets);

        IBakedModel wrapped = registry.getObject(wall);

        assertTrue(wrapped instanceof ContinuousGlassModel);
        assertSame(wrapped, registry.getObject(alias));
        assertSame(base, registry.getObject(item));
        assertSame(base, registry.getObject(other));
        assertSame(missing, registry.getObject(absent));
        ContinuousGlassModel.install(registry, missing, targets);
        assertSame(wrapped, registry.getObject(wall));
    }

    @Test
    void aResourceReloadUsesTheNewBaseModel() {
        ModelResourceLocation key = key("clear_glass_wall_00", "connections=10,meta=0,up=false");
        Set<String> targets = Collections.singleton("skysbuildingpieces:clear_glass_wall_00");
        RegistrySimple<ModelResourceLocation, IBakedModel> registry = new RegistrySimple<ModelResourceLocation, IBakedModel>();

        registry.putObject(key, new EmptyModel());
        ContinuousGlassModel.install(registry, null, targets);

        IBakedModel previous = registry.getObject(key);

        registry.putObject(key, new EmptyModel());
        ContinuousGlassModel.install(registry, null, targets);
        assertTrue(registry.getObject(key) instanceof ContinuousGlassModel);
        assertNotSame(previous, registry.getObject(key));
    }

    private static ModelResourceLocation key(String block, String variant) {
        return new ModelResourceLocation("skysbuildingpieces:" + block, variant);
    }

    private static final class EmptyModel implements IBakedModel {
        public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long random) {
            return Collections.emptyList();
        }

        public boolean isAmbientOcclusion() {
            return false;
        }

        public boolean isGui3d() {
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
            return ItemOverrideList.NONE;
        }
    }
}
