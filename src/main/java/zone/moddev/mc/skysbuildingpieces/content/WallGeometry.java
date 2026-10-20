package zone.moddev.mc.skysbuildingpieces.content;

import java.util.*;

/** Pixel bounds shared by wall collision, selection and the exterior mesh. */
public final class WallGeometry {
    public static final class Edge {
        public final int x0, y0, z0, x1, y1, z1;

        private Edge(int[] startPoint, int[] endPoint) {
            x0 = startPoint[0];
            y0 = startPoint[1];
            z0 = startPoint[2];
            x1 = endPoint[0];
            y1 = endPoint[1];
            z1 = endPoint[2];
        }
    }

    public static final class Bounds {
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        private Bounds(int minimumX, int minimumY, int minimumZ, int maximumX, int maximumY, int maximumZ) {
            minX = minimumX;
            minY = minimumY;
            minZ = minimumZ;
            maxX = maximumX;
            maxY = maximumY;
            maxZ = maximumZ;
        }
    }

    public static List<Bounds> boxes(int connections) {
        return boxes(connections, true);
    }

    public static List<Bounds> boxes(int connections, boolean post) {
        if (connections < 0 || connections > 15) {
            throw new IllegalArgumentException("Invalid wall connections: " + connections);
        }

        List<Bounds> result = new ArrayList<Bounds>();

        if (!post && connections == 5) {
            result.add(new Bounds(5, 0, 0, 11, 13, 16));
            return Collections.unmodifiableList(result);
        }
        if (!post && connections == 10) {
            result.add(new Bounds(0, 0, 5, 16, 13, 11));
            return Collections.unmodifiableList(result);
        }

        result.add(new Bounds(4, 0, 4, 12, 16, 12));

        if ((connections & 1) != 0) {
            result.add(new Bounds(5, 0, 0, 11, 13, 4));
        }
        if ((connections & 2) != 0) {
            result.add(new Bounds(12, 0, 5, 16, 13, 11));
        }
        if ((connections & 4) != 0) {
            result.add(new Bounds(5, 0, 12, 11, 13, 16));
        }
        if ((connections & 8) != 0) {
            result.add(new Bounds(0, 0, 5, 4, 13, 11));
        }

        return Collections.unmodifiableList(result);
    }

    private static final List<List<Edge>> OUTLINES = outlines();

    /** Exterior crease edges only; no enclosing box across the empty shoulders. */
    public static List<Edge> outline(int connections, boolean post) {
        if (connections < 0 || connections > 15) {
            throw new IllegalArgumentException("Invalid wall connections: " + connections);
        }

        return OUTLINES.get(connections + (post ? 16 : 0));
    }

    private static List<List<Edge>> outlines() {
        List<List<Edge>> result = new ArrayList<List<Edge>>();

        for (int connectionVariant = 0; connectionVariant < 32; connectionVariant++) {
            boolean[][][] filled = new boolean[16][16][16];

            // Merge the post and arms on a one-pixel grid before tracing their outline.
            // This removes selection edges where their boxes overlap.
            for (Bounds wallBounds : boxes(connectionVariant & 15, (connectionVariant & 16) != 0)) {
                for (int pixelX = wallBounds.minX; pixelX < wallBounds.maxX; pixelX++) {
                    for (int pixelY = wallBounds.minY; pixelY < wallBounds.maxY; pixelY++) {
                        for (int pixelZ = wallBounds.minZ; pixelZ < wallBounds.maxZ; pixelZ++) {
                            filled[pixelX][pixelY][pixelZ] = true;
                        }
                    }
                }
            }

            List<Edge> edges = new ArrayList<Edge>();

            // Walk each axis while holding the other two coordinates fixed.
            // Join consecutive crease pixels into one edge.
            for (int axis = 0; axis < 3; axis++) {
                for (int firstOffset = 0; firstOffset <= 16; firstOffset++) {
                    for (int secondOffset = 0; secondOffset <= 16; secondOffset++) {
                        int start = -1;

                        for (int axisPosition = 0; axisPosition <= 16; axisPosition++) {
                            boolean edge = axisPosition < 16
                                    && crease(filled, axis, axisPosition, firstOffset, secondOffset);

                            if (edge && start < 0) {
                                start = axisPosition;
                            }
                            if (!edge && start >= 0) {
                                int[] startPoint = new int[3];
                                int[] endPoint = new int[3];

                                startPoint[axis] = start;
                                endPoint[axis] = axisPosition;
                                startPoint[(axis + 1) % 3] = endPoint[(axis + 1) % 3] = firstOffset;
                                startPoint[(axis + 2) % 3] = endPoint[(axis + 2) % 3] = secondOffset;
                                edges.add(new Edge(startPoint, endPoint));
                                start = -1;
                            }
                        }
                    }
                }
            }

            result.add(Collections.unmodifiableList(edges));
        }

        return Collections.unmodifiableList(result);
    }

    private static boolean crease(boolean[][][] filled, int axis, int axisPosition, int firstOffset, int secondOffset) {
        // Four cells meet along each possible edge. Keep corners and diagonal
        // meetings, but discard flat boundaries.
        boolean occupiedBeforeBoth = occupied(filled, axis, axisPosition, firstOffset - 1, secondOffset - 1);
        boolean occupiedBeforeFirst = occupied(filled, axis, axisPosition, firstOffset - 1, secondOffset);

        boolean occupiedBeforeSecond = occupied(filled, axis, axisPosition, firstOffset, secondOffset - 1);
        boolean occupiedAfterBoth = occupied(filled, axis, axisPosition, firstOffset, secondOffset);

        int count = (occupiedBeforeBoth ? 1 : 0) + (occupiedBeforeFirst ? 1 : 0) + (occupiedBeforeSecond ? 1 : 0)
                + (occupiedAfterBoth ? 1 : 0);

        return count > 0 && count < 4 && (count != 2 || occupiedBeforeBoth == occupiedAfterBoth);
    }

    private static boolean occupied(boolean[][][] filled, int axis, int axisPosition, int firstOffset,
            int secondOffset) {
        if (firstOffset < 0 || firstOffset >= 16 || secondOffset < 0 || secondOffset >= 16) {
            return false;
        }

        return axis == 0 ? filled[axisPosition][firstOffset][secondOffset]
                : axis == 1 ? filled[secondOffset][axisPosition][firstOffset]
                        : filled[firstOffset][secondOffset][axisPosition];
    }

    private WallGeometry() {
    }
}
