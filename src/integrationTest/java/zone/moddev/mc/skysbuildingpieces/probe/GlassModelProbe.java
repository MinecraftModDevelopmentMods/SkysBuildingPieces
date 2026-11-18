package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Inspects the baked glass model only in the integrated client. */
final class GlassModelProbe {
    private GlassModelProbe() {
    }

    @SuppressWarnings("unchecked")
    static int joins(ServerLevel world, BlockPos position, BlockState state) {
        try {
            Class<?> type = zone.moddev.mc.skysbuildingpieces.client.ContinuousGlassModel.class;
            java.lang.reflect.Field field = type.getDeclaredField("JOINS");

            field.setAccessible(true);

            net.minecraftforge.client.model.data.ModelProperty<Integer> property = (net.minecraftforge.client.model.data.ModelProperty<Integer>) field
                    .get(null);
            var model = net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            require(type.isInstance(model), "The baked glass wall uses the continuous model");
            net.minecraftforge.client.model.data.ModelData data = model.getModelData(new RenderReadView(world), position, state,
                    net.minecraftforge.client.model.data.ModelData.EMPTY);

            require(data.has(property), "glass joins are present in render-only model data");
            return data.get(property);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect the native glass model data", failure);
        }
    }

    private record RenderReadView(ServerLevel world)
            implements net.minecraft.client.renderer.block.BlockAndTintGetter {
        @Override
        public BlockState getBlockState(BlockPos position) {
            return world.getBlockState(position);
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position) {
            return world.getBlockEntity(position);
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position) {
            return world.getFluidState(position);
        }

        @Override
        public int getHeight() {
            return world.getHeight();
        }

        @Override
        public int getMinY() {
            return world.getMinY();
        }

        @Override
        public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
            return world.getLightEngine();
        }

        @Override
        public net.minecraft.world.level.CardinalLighting cardinalLighting() {
            return net.minecraft.world.level.CardinalLighting.DEFAULT;
        }

        @Override
        public int getBlockTint(BlockPos position, net.minecraft.world.level.ColorResolver resolver) {
            return net.minecraft.client.renderer.block.BlockAndTintGetter.EMPTY.getBlockTint(position, resolver);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
