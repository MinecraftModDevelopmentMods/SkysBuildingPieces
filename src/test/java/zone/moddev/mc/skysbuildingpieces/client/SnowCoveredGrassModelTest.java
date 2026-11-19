package zone.moddev.mc.skysbuildingpieces.client;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap;
import static org.junit.jupiter.api.Assertions.*;

class SnowCoveredGrassModelTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.registerVanilla();
    }

    @Test
    void particlesWithoutModelDataUseTheOriginalMaterial() throws Exception {
        var original = new RecordingModel();
        var snowy = new RecordingModel();
        var model = wrapper(original, snowy);
        assertDoesNotThrow(() -> model.particleMaterial(null));
        assertSame(ModelData.EMPTY, original.particleData);
        assertEquals(0, snowy.particleCalls);
    }

    @Test
    void ordinaryPartsAcceptMissingModelData() throws Exception {
        var original = new RecordingModel();
        var model = wrapper(original, new RecordingModel());
        assertDoesNotThrow(() -> model.collectParts(RandomSource.create(0), new ArrayList<>(), null));
        assertSame(ModelData.EMPTY, original.partData);
    }

    @Test
    void modelDataCanStartEmptyWithoutLosingTheCoverDecision() throws Exception {
        var original = new RecordingModel();
        var model = wrapper(original, new RecordingModel());
        var world = (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[] {BlockAndTintGetter.class},
                (proxy, method, arguments) -> Blocks.AIR.defaultBlockState());
        assertNotNull(model.getModelData(world, BlockPos.ZERO, Blocks.GRASS_BLOCK.defaultBlockState(), null));
        assertSame(ModelData.EMPTY, original.worldData);
    }

    @Test
    @SuppressWarnings("unchecked")
    void coveredParticlesStillUseTheSnowyModel() throws Exception {
        var original = new RecordingModel();
        var snowy = new RecordingModel();
        var model = wrapper(original, snowy);
        var field = SnowCoveredGrassModel.class.getDeclaredField("COVERED");
        field.setAccessible(true);
        var covered = ModelData.builder().with((ModelProperty<Boolean>) field.get(null), true).build();
        model.particleMaterial(covered);
        assertSame(covered, snowy.particleData);
        assertEquals(0, original.particleCalls);
    }

    private static SnowCoveredGrassModel wrapper(BlockStateModel original, BlockStateModel snowy) throws Exception {
        var constructor = SnowCoveredGrassModel.class.getDeclaredConstructor(
                BlockStateModel.class, BlockStateModel.class, BakedQuad.MaterialInfo.class);
        constructor.setAccessible(true);
        return constructor.newInstance(original, snowy, null);
    }

    private static final class RecordingModel implements BlockStateModel {
        private ModelData particleData;
        private ModelData partData;
        private ModelData worldData;
        private int particleCalls;

        @Override
        public ModelData getModelData(BlockAndTintGetter world, BlockPos position,
                net.minecraft.world.level.block.state.BlockState state, ModelData data) {
            assertNotNull(data);
            worldData = data;
            return data;
        }

        @Override
        public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        }

        @Override
        public void collectParts(RandomSource random, List<BlockStateModelPart> parts, ModelData data) {
            assertNotNull(data);
            partData = data;
        }

        @Override
        public Material.Baked particleMaterial() {
            return null;
        }

        @Override
        public Material.Baked particleMaterial(ModelData data) {
            assertNotNull(data);
            particleData = data;
            particleCalls++;
            return null;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }
}
