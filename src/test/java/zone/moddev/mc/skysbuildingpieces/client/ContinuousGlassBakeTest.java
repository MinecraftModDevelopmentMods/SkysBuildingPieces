package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.util.RandomSource;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;

import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousGlassBakeTest {
    @Test
    void onlyWallBlockVariantsAreWrappedAndAliasesStayShared() {
        Map<ResourceLocation, BakedModel> registry = new LinkedHashMap<ResourceLocation, BakedModel>();
        BakedModel base = new EmptyModel();
        BakedModel missing = new EmptyModel();

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

        BakedModel wrapped = registry.get(wall);

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
        Map<ResourceLocation, BakedModel> registry = new LinkedHashMap<ResourceLocation, BakedModel>();

        registry.put(key, new EmptyModel());
        ContinuousGlassModel.install(registry, null, targets);

        BakedModel previous = registry.get(key);

        registry.put(key, new EmptyModel());
        ContinuousGlassModel.install(registry, null, targets);
        assertTrue(registry.get(key) instanceof ContinuousGlassModel);
        assertNotSame(previous, registry.get(key));
    }

    private static ModelResourceLocation key(String block, String variant) {
        return new ModelResourceLocation(new net.minecraft.resources.ResourceLocation("skysbuildingpieces:" + block), variant);
    }

    private static final class EmptyModel implements BakedModel {
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return Collections.emptyList();
        }

        public boolean useAmbientOcclusion() {
            return false;
        }

        public boolean isGui3d() {
            return true;
        }

        public boolean usesBlockLight() {
            return true;
        }

        public boolean isCustomRenderer() {
            return false;
        }

        public TextureAtlasSprite getParticleIcon() {
            return null;
        }

        public ItemTransforms getTransforms() {
            return null;
        }

        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
