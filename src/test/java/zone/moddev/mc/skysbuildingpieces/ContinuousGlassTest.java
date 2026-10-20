package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
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
        int[] data = quad(part, "west", 0);
        BakedQuad source = new BakedQuad(data, 3, EnumFacing.WEST, null, false, DefaultVertexFormats.BLOCK);
        List<BakedQuad> original = Collections.singletonList(source);
        IBakedModel base = new IBakedModel() {
            public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long random) {
                assertNull(state);
                return original;
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
        };
        ContinuousGlassModel model = new ContinuousGlassModel(base);

        assertSame(original, model.getQuads(null, null, 0));
        assertSame(original, model.getQuads(state(0), EnumFacing.WEST, 0));

        List<BakedQuad> joined = model.getQuads(state(5), EnumFacing.WEST, 0);

        assertSame(joined, model.getQuads(state(5), EnumFacing.WEST, 123));
        assertThrows(UnsupportedOperationException.class, () -> joined.clear());

        BakedQuad trimmed = joined.get(0);

        assertNotSame(source, trimmed);
        assertArrayEquals(GlassWallTexture.trim(data, 7, 0, 4, 5), trimmed.getVertexData());
        assertEquals(3, trimmed.getTintIndex());
        assertEquals(EnumFacing.WEST, trimmed.getFace());
        assertFalse(trimmed.shouldApplyDiffuseLighting());
        assertSame(source.getSprite(), trimmed.getSprite());
        assertSame(source.getFormat(), trimmed.getFormat());
        assertFalse(model.isAmbientOcclusion());
        assertTrue(model.isGui3d());
        assertFalse(model.isBuiltInRenderer());
        assertSame(base.getOverrides(), model.getOverrides());
        assertSame(base.getParticleTexture(), model.getParticleTexture());
        assertSame(base.getItemCameraTransforms(), model.getItemCameraTransforms());
    }

    private static IBlockState state(int joins) {
        return (IBlockState) Proxy.newProxyInstance(ContinuousGlassTest.class.getClassLoader(),
                new Class<?>[] { IExtendedBlockState.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getValue") && args[0] == GlassWallTexture.JOINS) {
                        return joins;
                    }
                    if (method.getName().equals("getClean")) {
                        return null;
                    }

                    throw new AssertionError("Unexpected state call: " + method.getName());
                });
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
