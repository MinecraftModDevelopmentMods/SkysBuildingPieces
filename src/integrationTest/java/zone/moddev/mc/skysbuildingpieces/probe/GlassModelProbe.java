package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Inspects the baked glass model only in the integrated client. */
final class GlassModelProbe {
    private GlassModelProbe() {
    }

    static int joins(ServerLevel world, BlockPos position, BlockState state) {
        try {
            Class<?> type = zone.moddev.mc.skysbuildingpieces.client.ContinuousGlassModel.class;
            var matchingNeighbours = type.getDeclaredMethod("matchingNeighbours",
                    net.minecraft.client.renderer.block.BlockAndTintGetter.class, BlockPos.class, BlockState.class);
            matchingNeighbours.setAccessible(true);
            var model = net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            require(type.isInstance(model), "The baked glass wall uses the continuous model");
            return (int) matchingNeighbours.invoke(model, new RenderReadView(world), position, state);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect joins in the native glass model", failure);
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
