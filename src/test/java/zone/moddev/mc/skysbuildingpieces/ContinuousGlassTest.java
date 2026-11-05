package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.util.RandomSource;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import zone.moddev.mc.skysbuildingpieces.client.ContinuousGlassModel;
import zone.moddev.mc.skysbuildingpieces.content.GlassWallTexture;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousGlassTest {
    @Test
    void everyModelFaceTrimsOnlyMatchingBoundaryRimsInEveryUvRotation() throws Exception {
        for (int connections = 0; connections < 16; connections++) {
            for (boolean post : new boolean[] { false, true }) {
                String name = "glass_wall_0_" + connections
                        + (!post && (connections == 5 || connections == 10) ? "_no_post" : "");
                JsonObject model = new JsonParser().parse(new String(
                        Files.readAllBytes(Paths
                                .get("src/main/resources/assets/skysbuildingpieces/models/block/" + name + ".json")),
                        StandardCharsets.UTF_8)).getAsJsonObject();

                for (JsonElement element : model.getAsJsonArray("elements")) {
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().getAsJsonObject("faces")
                            .entrySet()) {
                        String face = entry.getKey();
                        int normal = face.equals("west") || face.equals("east") ? 0
                                : face.equals("up") || face.equals("down") ? 1 : 2;
                        int u = normal == 0 ? 2 : 0;
                        int v = normal == 1 ? 2 : 1;

                        for (int rotation = 0; rotation < 4; rotation++) {
                            for (int joins = 0; joins < 16; joins++) {
                                int[] data = quad(element.getAsJsonObject(), face, rotation);
                                int[] before = data.clone();

                                int[] result = GlassWallTexture.trim(data, 7, 0, 4, joins);

                                assertArrayEquals(before, data, "shared baked vertices must not be modified");

                                for (int vertex = 0; vertex < 4; vertex++) {
                                    for (int component = 0; component < 7; component++) {
                                        if (component != 4 && component != 5) {
                                            assertEquals(data[vertex * 7 + component], result[vertex * 7 + component],
                                                    "geometry, colour and light stay unchanged");
                                        }
                                    }
                                    for (int component = 0; component < 2; component++) {
                                        int axis = (rotation % 2 == 0) == (component == 0) ? u : v;
                                        float p = value(data, vertex * 7 + axis);
                                        float min = component == 0 ? .125f : .25f;

                                        float old = value(data, vertex * 7 + 4 + component);
                                        float expected = old;

                                        boolean trim = axis != 1 && (p == 0 && (joins & (axis == 0 ? 8 : 1)) != 0
                                                || p == 1 && (joins & (axis == 0 ? 2 : 4)) != 0);

                                        if (trim) {
                                            expected = old == min ? old + .0625f / 16 : old - .0625f / 16;
                                        }

                                        assertEquals(expected, value(result, vertex * 7 + 4 + component), .0000001f,
                                                name + " " + face + " rotation=" + rotation + " joins=" + joins);
                                    }
                                }
                                if (joins == 0) {
                                    assertSame(data, result, "exposed ends and colour changes keep their border");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void bakedWrapperPreservesInventoryBordersAndQuadAttributesWithoutGraphics() {
        JsonObject part = new JsonParser().parse("{\"from\":[5,0,0],\"to\":[5,13,16]}").getAsJsonObject();
        int[] compact = quad(part, "west", 0);
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
        int[] data = new int[stride * 4];

        for (int vertex = 0; vertex < 4; vertex++) {
            System.arraycopy(compact, vertex * 7, data, vertex * stride, 7);
        }

        BakedQuad source = new BakedQuad(data, 3, Direction.WEST, null, false);
        List<BakedQuad> original = Collections.singletonList(source);
        BakedModel base = new BakedModel() {
            public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
                assertNull(state);
                return original;
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
        };
        ContinuousGlassModel model = new ContinuousGlassModel(base);

        assertSame(original, model.getQuads(null, null, RandomSource.create(0)));
        assertSame(original, model.getQuads(null, Direction.WEST, RandomSource.create(0), data(0), null));

        List<BakedQuad> joined = model.getQuads(null, Direction.WEST, RandomSource.create(0), data(5), null);

        assertSame(joined, model.getQuads(null, Direction.WEST, RandomSource.create(123), data(5), null));
        assertThrows(UnsupportedOperationException.class, () -> joined.clear());

        BakedQuad trimmed = joined.get(0);

        assertNotSame(source, trimmed);
        assertArrayEquals(GlassWallTexture.trim(data, stride, 0, 4, 5), trimmed.getVertices());
        assertEquals(3, trimmed.getTintIndex());
        assertEquals(Direction.WEST, trimmed.getDirection());
        assertFalse(trimmed.isShade());
        assertEquals(source.hasAmbientOcclusion(), trimmed.hasAmbientOcclusion());
        assertSame(source.getSprite(), trimmed.getSprite());
        assertEquals(stride * 4, trimmed.getVertices().length);
        assertFalse(model.useAmbientOcclusion());
        assertTrue(model.isGui3d());
        assertTrue(model.usesBlockLight());
        assertFalse(model.isCustomRenderer());
        assertSame(base.getOverrides(), model.getOverrides());
        assertSame(base.getParticleIcon(), model.getParticleIcon());
        assertSame(base.getTransforms(), model.getTransforms());
    }

    private static ModelData data(int joins) {
        try {
            java.lang.reflect.Field field = ContinuousGlassModel.class.getDeclaredField("JOINS");

            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            ModelProperty<Integer> property = (ModelProperty<Integer>) field.get(null);

            return ModelData.builder().with(property, joins).build();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static int[] quad(JsonObject part, String face, int rotation) {
        int axis = face.equals("west") || face.equals("east") ? 0 : face.equals("up") || face.equals("down") ? 1 : 2;
        int u = axis == 0 ? 2 : 0;
        int v = axis == 1 ? 2 : 1;

        int[] data = new int[28];

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            for (int a = 0; a < 3; a++) {
                boolean high = a == axis ? (face.equals("east") || face.equals("up") || face.equals("south"))
                        : a == u ? directionIndex >= 2 : directionIndex == 1 || directionIndex == 2;

                data[directionIndex * 7 + a] = Float
                        .floatToRawIntBits(part.getAsJsonArray(high ? "to" : "from").get(a).getAsFloat() / 16);
            }

            int rotated = (directionIndex + rotation) % 4;

            data[directionIndex * 7 + 3] = 0xA0B0C000 + directionIndex;
            data[directionIndex * 7 + 4] = Float.floatToRawIntBits(.125f + (rotated >= 2 ? .0625f : 0));
            data[directionIndex * 7 + 5] = Float.floatToRawIntBits(.25f + (rotated == 1 || rotated == 2 ? .0625f : 0));
            data[directionIndex * 7 + 6] = 0x00100010 + directionIndex;
        }

        return data;
    }

    private static float value(int[] data, int offset) {
        return Float.intBitsToFloat(data[offset]);
    }
}
