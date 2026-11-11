package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.data.ModelData;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Inspect the baked support model beneath every snow footprint, including its removal. */
public final class SnowCoverClientChecks {
    public static void run(Minecraft client) {
        var position = client.player.blockPosition();
        BlockState[] above = {Blocks.AIR.defaultBlockState()};
        BlockAndTintGetter view = (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(), new Class<?>[] {BlockAndTintGetter.class},
                (proxy, method, arguments) -> method.getName().equals("getBlockState")
                        && position.above().equals(arguments[0]) ? above[0] : method.invoke(client.level, arguments));
        int checked = 0;
        for (boolean snowyState : new boolean[] {false, true}) {
            BlockState support = Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyBlock.SNOWY, snowyState);
            BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(support);
            for (Shape shape : new Shape[] {Shape.SLAB, Shape.VERTICAL_SLAB,
                    Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS}) {
                for (int orientation = 0; orientation < shape.states; orientation++) {
                    BlockState piece = Pieces.state("minecraft:snow", shape, orientation);
                    boolean resting = (((PieceBlock) piece.getBlock()).mask(piece) & 15) != 0;
                    above[0] = piece;
                    ModelData covered = model.getModelData(view, position, support, ModelData.EMPTY);
                    checkTop(model, covered, resting, piece.toString());
                    above[0] = piece.setValue(BlockStateProperties.WATERLOGGED, true);
                    checkTop(model, model.getModelData(view, position, support, covered), false, "wet " + piece);
                    above[0] = Blocks.AIR.defaultBlockState();
                    checkTop(model, model.getModelData(view, position, support, covered), false, "removed " + piece);
                    checked += 3;
                }
            }
            for (Shape shape : new Shape[] {Shape.HORIZONTAL_STEP, Shape.CORNER}) {
                for (int orientation = 0; orientation < shape.states; orientation++) {
                    BlockState piece = Pieces.state("minecraft:grass", shape, orientation);
                    above[0] = piece.setValue(SnowyBlock.SNOWY, true);
                    ModelData covered = model.getModelData(view, position, support, ModelData.EMPTY);
                    checkTop(model, covered, (((PieceBlock) piece.getBlock()).mask(piece) & 15) != 0, "snowy " + piece);
                    above[0] = piece.setValue(SnowyBlock.SNOWY, false);
                    checkTop(model, model.getModelData(view, position, support, covered), false, "thawed " + piece);
                    checked += 2;
                }
            }
            for (BlockState control : new BlockState[] {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.SNOW.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState()}) {
                above[0] = control;
                checkTop(model, model.getModelData(view, position, support, ModelData.EMPTY), false, "vanilla " + control);
                checked++;
            }
            above[0] = Blocks.POWDER_SNOW.defaultBlockState();
            checkTop(model, model.getModelData(view, position, support, ModelData.EMPTY), true, "powder snow");
            checked++;
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_SNOW_SUPPORT_CLIENT_PASS cases={} removal=true wet_controls=true", checked);
    }

    private static void checkTop(BlockStateModel model, ModelData data, boolean snow, String label) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(0), parts, data);
        List<BakedQuad> tops = new ArrayList<>();
        for (var part : parts) {
            for (Direction face : new Direction[] {Direction.UP, null}) {
                part.getQuads(face).stream().filter(quad -> quad.direction() == Direction.UP).forEach(tops::add);
            }
        }
        require(!tops.isEmpty(), "Missing support top: " + label);
        for (BakedQuad top : tops) {
            var sprite = top.materialInfo().sprite();
            String expected = snow ? "minecraft:block/snow" : "minecraft:block/grass_block_top";
            require(sprite.contents().name().toString().equals(expected) && (!snow || !top.materialInfo().isTinted()),
                    "Wrong support texture: " + label + " got=" + sprite.contents().name());
            for (int vertex = 0; vertex < 4; vertex++) {
                float horizontal = UVPair.unpackU(top.packedUV(vertex));
                float vertical = UVPair.unpackV(top.packedUV(vertex));
                require(top.position(vertex).y() == 1 && horizontal >= sprite.getU0() - .000001F
                                && horizontal <= sprite.getU1() + .000001F && vertical >= sprite.getV0() - .000001F
                                && vertical <= sprite.getV1() + .000001F,
                        "Wrong support height or atlas coordinates: " + label);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private SnowCoverClientChecks() {
    }
}
