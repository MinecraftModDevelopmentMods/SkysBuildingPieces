package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import zone.moddev.mc.skysbuildingpieces.client.SnowCoveredGrassModel;

/** Read actual baked vanilla/BOP models, including uncullable top quads. */
final class SnowVisualChecks {
    static void run(Minecraft minecraft) {
        int states = 0;
        int tops = 0;
        for (Block block : Block.REGISTRY) {
            if (!(block instanceof BlockGrass)) {
                continue;
            }
            for (IBlockState state : block.getBlockState().getValidStates()) {
                IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);
                if (!state.getValue(BlockGrass.SNOWY)) {
                    require(!(model instanceof SnowCoveredGrassModel), "ordinary grass stays unchanged");
                    continue;
                }
                require(model instanceof SnowCoveredGrassModel, "snowy grass support model: " + state);
                for (EnumFacing side : new EnumFacing[] { null, EnumFacing.UP }) {
                    for (BakedQuad quad : model.getQuads(state, side, 0)) {
                        if (quad.getFace() == EnumFacing.UP) {
                            require(quad.getSprite().getIconName().equals("minecraft:blocks/snow"), "snow top texture");
                            require(!quad.hasTintIndex(), "snow top is not biome tinted");
                            tops++;
                        }
                    }
                }
                require(model.getParticleTexture() != null, "support particle texture");
                states++;
            }
        }
        require(states > 0 && tops >= states, "snow support models were exercised");
        System.out.println("BUILDING_PIECES_SNOW_MODELS_PASS states=" + states + " tops=" + tops);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private SnowVisualChecks() {
    }
}
