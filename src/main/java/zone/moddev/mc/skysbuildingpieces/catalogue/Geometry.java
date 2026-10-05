package zone.moddev.mc.skysbuildingpieces.catalogue;

/** Occupancy of eight half-block cells; pure and exhaustively testable. */
public final class Geometry {
    private Geometry() { }
    public static int mask(Shape shape, int orientation) {
        if (orientation < 0 || orientation >= shape.states) throw new IllegalArgumentException("Invalid orientation");
        if (shape == Shape.WALL || shape == Shape.PANE) return 255;
        int result = 0;
        for (int y=0;y<2;y++) for (int z=0;z<2;z++) for (int x=0;x<2;x++) {
            boolean include;
            int rotation = orientation & 3;
            boolean edge = rotation == 0 ? z==0 : rotation == 1 ? x==1 : rotation == 2 ? z==1 : x==0;
            boolean corner = (x == (rotation==0 || rotation==3 ? 0 : 1)) &&
                    (z == (rotation==0 || rotation==1 ? 0 : 1));
            switch (shape) {
                case SLAB: include = y == (orientation==0 ? 1 : 0); break;
                // EnumFacing horizontal indices: south, west, north, east.
                case VERTICAL_SLAB: include = orientation==0 ? z==1 : orientation==1 ? x==0 : orientation==2 ? z==0 : x==1; break;
                case HORIZONTAL_STEP: include = edge && y == (orientation<4 ? 1 : 0); break;
                case VERTICAL_STEP: include = corner; break;
                case STEP: include = orientation<8 ? edge && y==(orientation<4 ? 1 : 0) : corner; break;
                case CORNER: include = corner && y==(orientation<4 ? 1 : 0); break;
                // Vanilla stairs metadata: east, west, south, north, plus upper-half bit.
                case STAIRS: include = y == (orientation<4 ? 0 : 1) ||
                        (rotation==0 ? x==1 : rotation==1 ? x==0 : rotation==2 ? z==1 : z==0); break;
                default: throw new AssertionError(shape);
            }
            if (include) result |= 1 << (x | z<<1 | y<<2);
        }
        return result;
    }
    public static boolean completeTop(int mask) { return (mask & 240) == 240; }
    public static boolean completeFace(int mask, int face) {
        int faceMask = face==0 ? 15 : face==1 ? 240 : face==2 ? 51 : face==3 ? 204 : face==4 ? 85 : 170;
        return (mask & faceMask)==faceMask;
    }
    public static int orientationFor(Shape shape, int mask) {
        for (int i=0;i<shape.states;i++) if(mask(shape,i)==mask) return i;
        return -1;
    }
    public static int stairs(int orientation,String shape) {
        int straight=mask(Shape.STAIRS,orientation),base=orientation<4?15:240;
        if(shape.equals("straight"))return straight;
        int facing=orientation&3;
        int[] left={3,2,0,1},right={2,3,1,0};
        int other=mask(Shape.STAIRS,(shape.endsWith("left")?left[facing]:right[facing])+(orientation&4));
        return shape.startsWith("inner") ? straight|other : base|(straight&other);
    }
}
