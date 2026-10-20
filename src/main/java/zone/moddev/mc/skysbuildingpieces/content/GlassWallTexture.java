package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraftforge.common.property.IUnlistedProperty;

/** Render-only joins and texture coordinates for matching glass wall sections. */
public final class GlassWallTexture {
    public static final IUnlistedProperty<Integer> JOINS = new IUnlistedProperty<Integer>() {
        public String getName() {
            return "glass_joins";
        }

        public boolean isValid(Integer value) {
            return value != null && value >= 0 && value < 16;
        }

        public Class<Integer> getType() {
            return Integer.class;
        }

        public String valueToString(Integer value) {
            return value.toString();
        }
    };

    /** Crops one texture pixel only at an outer edge joined to matching glass. */
    public static int[] trim(int[] vertices, int stride, int position, int uvOffset, int joins) {
        if (vertices.length != stride * 4 || position < 0 || position + 2 >= stride || uvOffset < 0
                || uvOffset + 1 >= stride || joins < 0 || joins > 15) {
            throw new IllegalArgumentException("Invalid glass-wall quad");
        }

        int[] result = vertices;

        for (int axis : new int[] { 0, 2 }) {
            float minimumPosition = Float.POSITIVE_INFINITY;
            float maximumPosition = Float.NEGATIVE_INFINITY;

            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                float vertexPosition = value(vertices, vertexIndex * stride + position + axis);

                minimumPosition = Math.min(minimumPosition, vertexPosition);
                maximumPosition = Math.max(maximumPosition, vertexPosition);
            }

            boolean start = Math.abs(minimumPosition) < .00001f && (joins & (axis == 0 ? 8 : 1)) != 0;
            boolean joinsPositiveEnd = Math.abs(maximumPosition - 1) < .00001f && (joins & (axis == 0 ? 2 : 4)) != 0;

            if (maximumPosition - minimumPosition < .00001f || !start && !joinsPositiveEnd) {
                continue;
            }

            int paired = -1;

            for (int vertexIndex = 1; vertexIndex < 4; vertexIndex++) {
                boolean sameOtherCoordinates = true;

                for (int coordinateAxis = 0; coordinateAxis < 3; coordinateAxis++) {
                    if (coordinateAxis != axis && Math.abs(value(vertices, position + coordinateAxis)
                            - value(vertices, vertexIndex * stride + position + coordinateAxis)) > .00001f) {
                        sameOtherCoordinates = false;
                    }
                }
                if (sameOtherCoordinates && Math.abs(value(vertices, position + axis)
                        - value(vertices, vertexIndex * stride + position + axis)) > .00001f) {
                    paired = vertexIndex;
                    break;
                }
            }
            if (paired < 0) {
                continue;
            }
            for (int component = 0; component < 2; component++) {
                float delta = (value(result, paired * stride + uvOffset + component)
                        - value(result, uvOffset + component))
                        / (value(vertices, paired * stride + position + axis) - value(vertices, position + axis))
                        * (maximumPosition - minimumPosition);

                if (Math.abs(delta) < .0000001f) {
                    continue;
                }
                if (result == vertices) {
                    result = vertices.clone();
                }
                for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                    float positionFraction = (value(vertices, vertexIndex * stride + position + axis) - minimumPosition)
                            / (maximumPosition - minimumPosition);
                    float shift = delta
                            * ((start ? 1 - positionFraction : 0) - (joinsPositiveEnd ? positionFraction : 0)) / 16;
                    int offset = vertexIndex * stride + uvOffset + component;

                    result[offset] = Float.floatToRawIntBits(value(result, offset) + shift);
                }
            }
        }

        return result;
    }

    private static float value(int[] data, int offset) {
        return Float.intBitsToFloat(data[offset]);
    }

    private GlassWallTexture() {
    }
}
