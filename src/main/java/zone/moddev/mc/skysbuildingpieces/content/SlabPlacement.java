package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.util.EnumFacing;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Face-local centre/edge selection, independent of any other mod's placement handlers. */
public final class SlabPlacement {
    private static final double EDGE = .22f;

    /** The half of the destination block occupied by a slab. */
    public static EnumFacing side(EnumFacing face, float x, float y, float z) {
        if (centre(face, x, y, z)) return face.getOpposite();
        if (face.getAxis() == EnumFacing.Axis.Y) {
            if (x >= z) return x < 1 - (double) z ? EnumFacing.NORTH : EnumFacing.EAST;
            return x >= 1 - (double) z ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
        double right = right(face, x, z);
        if (right >= y) return right < 1 - (double) y ? EnumFacing.DOWN : face.rotateY();
        return right >= 1 - (double) y ? EnumFacing.UP : face.rotateYCCW();
    }

    /** Explicit vertical-slab items stay vertical, including clicks near a side edge. */
    public static EnumFacing verticalSide(EnumFacing face, float x, float y, float z) {
        if (face.getAxis() == EnumFacing.Axis.Y) {
            if (x >= z) return x < 1 - (double) z ? EnumFacing.NORTH : EnumFacing.EAST;
            return x >= 1 - (double) z ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
        if (centre(face, x, y, z)) return face.getOpposite();
        return right(face, x, z) >= y ? face.rotateY() : face.rotateYCCW();
    }

    public static Shape shape(EnumFacing side) {
        return side.getAxis() == EnumFacing.Axis.Y ? Shape.SLAB : Shape.VERTICAL_SLAB;
    }

    public static int orientation(EnumFacing side) {
        return side == EnumFacing.UP ? 0 : side == EnumFacing.DOWN ? 1 : side.getHorizontalIndex();
    }

    private static boolean centre(EnumFacing face, float x, float y, float z) {
        double right = right(face, x, z), up = face.getAxis() == EnumFacing.Axis.Y ? z : y;
        return right >= EDGE && right <= 1 - EDGE && up >= EDGE && up <= 1 - EDGE;
    }

    private static double right(EnumFacing face, float x, float z) {
        switch (face.getAxis()) {
            case X: return face == EnumFacing.EAST ? z : 1 - (double) z;
            case Y: return face == EnumFacing.UP ? 1 - (double) x : x;
            default: return face == EnumFacing.SOUTH ? 1 - (double) x : x;
        }
    }

    private SlabPlacement() { }
}
