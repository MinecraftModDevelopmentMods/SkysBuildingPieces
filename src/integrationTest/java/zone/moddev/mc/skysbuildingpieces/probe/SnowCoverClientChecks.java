package zone.moddev.mc.skysbuildingpieces.probe;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Check the real baked grass model with each snow footprint and its removal. */
public final class SnowCoverClientChecks {
    public static void run(Minecraft client) {
        BlockPos position = client.player.blockPosition();
        RenderView view = new RenderView(client.level, position);
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        int checked = 0;

        for (boolean snowyState : new boolean[] { false, true }) {
            BlockState support = grass.setValue(SnowyDirtBlock.SNOWY, snowyState);
            BakedModel model = client.getBlockRenderer().getBlockModel(support);
            for (Shape shape : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB,
                    Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS }) {
                for (int orientation = 0; orientation < shape.states; orientation++) {
                    BlockState piece = Pieces.state("minecraft:snow", shape, orientation);
                    PieceBlock block = (PieceBlock) piece.getBlock();
                    boolean resting = (block.mask(piece) & 15) != 0;
                    view.above = piece;
                    ModelData covered = model.getModelData(view, position, support, ModelData.EMPTY);
                    checkTop(model, support, covered, resting, shape + " orientation=" + orientation);
                    if (resting) {
                        checkSide(model, support, covered);
                    }

                    view.above = piece.setValue(BlockStateProperties.WATERLOGGED, true);
                    ModelData wet = model.getModelData(view, position, support, covered);
                    checkTop(model, support, wet, false, "wet " + shape);
                    view.above = Blocks.AIR.defaultBlockState();
                    ModelData removed = model.getModelData(view, position, support, covered);
                    checkTop(model, support, removed, false, "removed " + shape);
                    checked += 3;
                }
            }

            for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.CORNER }) {
                for (int orientation = 0; orientation < shape.states; orientation++) {
                    BlockState piece = Pieces.state("minecraft:grass", shape, orientation);
                    boolean resting = (((PieceBlock) piece.getBlock()).mask(piece) & 15) != 0;
                    view.above = piece.setValue(SnowyDirtBlock.SNOWY, true);
                    ModelData covered = model.getModelData(view, position, support, ModelData.EMPTY);
                    checkTop(model, support, covered, resting, "snowy grass " + shape);
                    view.above = piece.setValue(SnowyDirtBlock.SNOWY, false);
                    checkTop(model, support, model.getModelData(view, position, support, covered), false,
                            "thawed grass " + shape);
                    checked += 2;
                }
            }

            for (BlockState control : new BlockState[] { Blocks.AIR.defaultBlockState(),
                    Blocks.STONE.defaultBlockState(), Blocks.ICE.defaultBlockState(),
                    Blocks.SNOW.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState() }) {
                view.above = control;
                checkTop(model, support, model.getModelData(view, position, support, ModelData.EMPTY), false,
                        "vanilla control " + control);
                checked++;
            }
        }

        view.above = Blocks.POWDER_SNOW.defaultBlockState();
        BakedModel powderSupport = client.getBlockRenderer().getBlockModel(grass);
        checkTop(powderSupport, grass, powderSupport.getModelData(view, position, grass, ModelData.EMPTY), true,
                "powder snow covers the grass too");
        checked++;

        BakedModel item = client.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(Blocks.GRASS_BLOCK),
                client.level, client.player, 0);
        require(top(item, null, ModelData.EMPTY, null).stream().allMatch(SnowCoverClientChecks::grassTexture),
                "The grass inventory item keeps its normal top");
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_SNOW_SUPPORT_CLIENT_PASS cases=" + checked
                        + " removal=true wet_controls=true inventory=true");
    }

    private static void checkTop(BakedModel model, BlockState state, ModelData data, boolean snow, String label) {
        for (RenderType layer : new RenderType[] { null, RenderType.cutoutMipped() }) {
            List<BakedQuad> quads = top(model, state, data, layer);
            require(!quads.isEmpty(), "The support top stays visible: " + label);
            for (BakedQuad quad : quads) {
                require(snow ? quad.getSprite().contents().name().toString().equals("minecraft:block/snow")
                                && quad.getTintIndex() == -1 : grassTexture(quad),
                        "The support top matches its cover: " + label + " texture="
                                + quad.getSprite().contents().name());
                if (snow) {
                    checkSnowVertices(quad);
                }
            }
        }
    }

    private static void checkSnowVertices(BakedQuad quad) {
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
        int position = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.POSITION) / Integer.BYTES;
        int texture = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV0) / Integer.BYTES;
        int[] vertices = quad.getVertices();
        var sprite = quad.getSprite();
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * stride;
            require(Float.intBitsToFloat(vertices[base + position + 1]) == 1,
                    "The snow covering keeps the original grass surface height");
            float horizontal = Float.intBitsToFloat(vertices[base + texture]);
            float vertical = Float.intBitsToFloat(vertices[base + texture + 1]);
            require(horizontal >= sprite.getU0() - 0.000001F && horizontal <= sprite.getU1() + 0.000001F
                            && vertical >= sprite.getV0() - 0.000001F && vertical <= sprite.getV1() + 0.000001F,
                    "The support uses the snow texture's atlas coordinates");
        }
    }

    private static List<BakedQuad> top(BakedModel model, BlockState state, ModelData data, RenderType layer) {
        List<BakedQuad> result = new ArrayList<>();
        for (Direction face : new Direction[] { Direction.UP, null }) {
            model.getQuads(state, face, RandomSource.create(0), data, layer).stream()
                    .filter(quad -> quad.getDirection() == Direction.UP).forEach(result::add);
        }
        return result;
    }

    private static void checkSide(BakedModel model, BlockState state, ModelData data) {
        List<BakedQuad> quads = model.getQuads(state, Direction.NORTH, RandomSource.create(0), data, null);
        require(!quads.isEmpty() && quads.stream().allMatch(quad ->
                        quad.getSprite().contents().name().toString().equals("minecraft:block/grass_block_snow")
                                && quad.getTintIndex() == -1),
                "Snow-covered supports use the snowy grass sides without biome tint");
    }

    private static boolean grassTexture(BakedQuad quad) {
        return quad.getSprite().contents().name().toString().equals("minecraft:block/grass_block_top");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RenderView implements BlockAndTintGetter {
        private final BlockAndTintGetter original;
        private final BlockPos support;
        private BlockState above = Blocks.AIR.defaultBlockState();

        private RenderView(BlockAndTintGetter original, BlockPos support) {
            this.original = original;
            this.support = support;
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            return position.equals(support.above()) ? above : original.getBlockState(position);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos position) {
            return original.getBlockEntity(position);
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }

        @Override
        public float getShade(Direction face, boolean shade) {
            return original.getShade(face, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return original.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos position, ColorResolver resolver) {
            return original.getBlockTint(position, resolver);
        }

        @Override
        public int getHeight() {
            return original.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return original.getMinBuildHeight();
        }
    }

    private SnowCoverClientChecks() {
    }
}
