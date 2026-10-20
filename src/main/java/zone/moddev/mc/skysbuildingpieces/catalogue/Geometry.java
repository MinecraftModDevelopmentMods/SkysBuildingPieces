package zone.moddev.mc.skysbuildingpieces.catalogue;

/** Describes a piece as occupied cells in a 2-by-2-by-2 grid. */
public final class Geometry {
    private Geometry() {
    }

    public static int mask(Shape shape, int orientation) {
        if (orientation < 0 || orientation >= shape.states) {
            throw new IllegalArgumentException("Invalid orientation");
        }
        if (shape == Shape.WALL || shape == Shape.PANE) {
            return 255;
        }

        int occupiedCells = 0;

        // Each grid cell occupies one eighth of a block. Corners fill one cell,
        // steps fill two and slabs fill four.
        for (int cellY = 0; cellY < 2; cellY++) {
            for (int cellZ = 0; cellZ < 2; cellZ++) {
                for (int cellX = 0; cellX < 2; cellX++) {
                    boolean cellIsOccupied;
                    int horizontalRotation = orientation & 3;
                    boolean cellIsOnFacingEdge = horizontalRotation == 0 ? cellZ == 0
                            : horizontalRotation == 1 ? cellX == 1 : horizontalRotation == 2 ? cellZ == 1 : cellX == 0;
                    boolean cellIsInFacingCorner = (cellX == (horizontalRotation == 0 || horizontalRotation == 3 ? 0
                            : 1)) && (cellZ == (horizontalRotation == 0 || horizontalRotation == 1 ? 0 : 1));

                    switch (shape) {
                        case SLAB:
                            cellIsOccupied = cellY == (orientation == 0 ? 1 : 0);
                            break;
                        // EnumFacing horizontal indices: south, west, north, east.
                        case VERTICAL_SLAB:
                            cellIsOccupied = orientation == 0 ? cellZ == 1
                                    : orientation == 1 ? cellX == 0 : orientation == 2 ? cellZ == 0 : cellX == 1;
                            break;
                        case HORIZONTAL_STEP:
                            cellIsOccupied = cellIsOnFacingEdge && cellY == (orientation < 4 ? 1 : 0);
                            break;
                        case VERTICAL_STEP:
                            cellIsOccupied = cellIsInFacingCorner;
                            break;
                        case STEP:
                            cellIsOccupied = orientation < 8 ? cellIsOnFacingEdge && cellY == (orientation < 4 ? 1 : 0)
                                    : cellIsInFacingCorner;
                            break;
                        case CORNER:
                            cellIsOccupied = cellIsInFacingCorner && cellY == (orientation < 4 ? 1 : 0);
                            break;
                        // Vanilla stairs metadata: east, west, south, north, plus upper-half bit.
                        case STAIRS:
                            cellIsOccupied = cellY == (orientation < 4 ? 0 : 1) || (horizontalRotation == 0 ? cellX == 1
                                    : horizontalRotation == 1 ? cellX == 0
                                            : horizontalRotation == 2 ? cellZ == 1 : cellZ == 0);
                            break;
                        default:
                            throw new AssertionError(shape);
                    }
                    if (cellIsOccupied) {
                        // Bits run west to east, then north to south, then bottom to top.
                        occupiedCells |= 1 << (cellX | cellZ << 1 | cellY << 2);
                    }
                }
            }
        }

        return occupiedCells;
    }

    /** True only when all four upper cells are filled. */
    public static boolean completeTop(int mask) {
        return (mask & 240) == 240;
    }

    public static boolean completeFace(int mask, int face) {
        // Face indices follow EnumFacing: down, up, north, south, west and east.
        int faceMask = face == 0 ? 15 : face == 1 ? 240 : face == 2 ? 51 : face == 3 ? 204 : face == 4 ? 85 : 170;

        return (mask & faceMask) == faceMask;
    }

    public static int orientationFor(Shape shape, int mask) {
        for (int candidateOrientation = 0; candidateOrientation < shape.states; candidateOrientation++) {
            if (mask(shape, candidateOrientation) == mask) {
                return candidateOrientation;
            }
        }

        return -1;
    }

    public static int stairs(int orientation, String shape) {
        int straightStairMask = mask(Shape.STAIRS, orientation);
        int baseLayerMask = orientation < 4 ? 15 : 240;

        if (shape.equals("straight")) {
            return straightStairMask;
        }

        int horizontalFacing = orientation & 3;
        int[] leftTurnFacings = { 3, 2, 0, 1 };
        int[] rightTurnFacings = { 2, 3, 1, 0 };

        int adjacentStairMask = mask(Shape.STAIRS,
                (shape.endsWith("left") ? leftTurnFacings[horizontalFacing] : rightTurnFacings[horizontalFacing])
                        + (orientation & 4));

        return shape.startsWith("inner") ? straightStairMask | adjacentStairMask
                : baseLayerMask | (straightStairMask & adjacentStairMask);
    }
}
