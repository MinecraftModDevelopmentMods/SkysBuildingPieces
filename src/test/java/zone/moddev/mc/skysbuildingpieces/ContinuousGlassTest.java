package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.util.RandomSource;


import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.joml.Vector3f;
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
    void bakedWrapperKeepsTheFaceAndChangesOnlyTheJoinedTexture() {
        JsonObject part = new JsonParser().parse("{\"from\":[5,0,0],\"to\":[5,13,16]}").getAsJsonObject();
        int[] vertices = quad(part, "west", 0);
        Vector3f[] positions = new Vector3f[4];
        long[] uv = new long[4];
        for (int vertex = 0; vertex < positions.length; vertex++) {
            int offset = vertex * 7;
            positions[vertex] = new Vector3f(value(vertices, offset),
                    value(vertices, offset + 1), value(vertices, offset + 2));
            uv[vertex] = UVPair.pack(value(vertices, offset + 4), value(vertices, offset + 5));
        }
        BakedQuad source = new BakedQuad(positions[0], positions[1], positions[2], positions[3],
                uv[0], uv[1], uv[2], uv[3], 3, Direction.WEST, null, false, 4, false);
        List<BakedQuad> original = List.of(source);
        BlockModelPart basePart = new BlockModelPart() {
            @Override
            public List<BakedQuad> getQuads(Direction face) {
                return face == Direction.WEST ? original : List.of();
            }

            @Override
            public boolean useAmbientOcclusion() {
                return false;
            }

            @Override
            public TextureAtlasSprite particleIcon() {
                return null;
            }

            @Override
            public ChunkSectionLayer layer() {
                return ChunkSectionLayer.TRANSLUCENT;
            }
        };
        BlockStateModel base = new BlockStateModel() {
            @Override
            public void collectParts(RandomSource random, List<BlockModelPart> parts) {
                parts.add(basePart);
            }

            @Override
            public TextureAtlasSprite particleIcon() {
                return null;
            }
        };
        ContinuousGlassModel model = new ContinuousGlassModel(base);
        assertSame(basePart, model.collectParts(RandomSource.create(0)).getFirst());
        assertSame(basePart, model.collectParts(RandomSource.create(0), data(0), null).getFirst());
        BlockModelPart joined = model.collectParts(RandomSource.create(0), data(5), null).getFirst();
        assertSame(joined, model.collectParts(RandomSource.create(123), data(5), null).getFirst());
        assertEquals(ChunkSectionLayer.TRANSLUCENT, joined.layer());
        assertFalse(joined.useAmbientOcclusion());
        assertSame(basePart.particleIcon(), joined.particleIcon());

        List<BakedQuad> quads = joined.getQuads(Direction.WEST);
        assertThrows(UnsupportedOperationException.class, quads::clear);
        BakedQuad trimmed = quads.getFirst();
        assertNotSame(source, trimmed);
        int[] expected = GlassWallTexture.trim(vertices, 7, 0, 4, 5);
        for (int vertex = 0; vertex < positions.length; vertex++) {
            assertSame(source.position(vertex), trimmed.position(vertex));
            int offset = vertex * 7;
            assertEquals(UVPair.pack(value(expected, offset + 4), value(expected, offset + 5)),
                    trimmed.packedUV(vertex));
        }
        assertEquals(source.tintIndex(), trimmed.tintIndex());
        assertEquals(source.direction(), trimmed.direction());
        assertEquals(source.shade(), trimmed.shade());
        assertEquals(source.ambientOcclusion(), trimmed.ambientOcclusion());
        assertEquals(source.lightEmission(), trimmed.lightEmission());
        assertSame(source.sprite(), trimmed.sprite());
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
