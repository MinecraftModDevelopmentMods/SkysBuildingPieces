package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.util.EnumFacing;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Face-local centre/edge selection, independent of any other mod's placement handlers. */
public final class SlabPlacement {
    private static final double EDGE = .22f;

    /** The half of the destination block occupied by a slab. */
    public static EnumFacing side(EnumFacing face, float hitX, float hitY, float hitZ) {
        if (centre(face, hitX, hitY, hitZ)) {
            return face.getOpposite();
        }
        if (face.getAxis() == EnumFacing.Axis.Y) {
            if (hitX >= hitZ) {
                return hitX < 1 - (double) hitZ ? EnumFacing.NORTH : EnumFacing.EAST;
            }

            return hitX >= 1 - (double) hitZ ? EnumFacing.SOUTH : EnumFacing.WEST;
        }

        double right = right(face, hitX, hitZ);

        if (right >= hitY) {
            return right < 1 - (double) hitY ? EnumFacing.DOWN : face.rotateY();
        }

        return right >= 1 - (double) hitY ? EnumFacing.UP : face.rotateYCCW();
    }

    /** Explicit vertical-slab items stay vertical, including clicks near a side edge. */
    public static EnumFacing verticalSide(EnumFacing face, float hitX, float hitY, float hitZ) {
        if (face.getAxis() == EnumFacing.Axis.Y) {
            if (hitX >= hitZ) {
                return hitX < 1 - (double) hitZ ? EnumFacing.NORTH : EnumFacing.EAST;
            }

            return hitX >= 1 - (double) hitZ ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
        if (centre(face, hitX, hitY, hitZ)) {
            return face.getOpposite();
        }

        return right(face, hitX, hitZ) >= hitY ? face.rotateY() : face.rotateYCCW();
    }

    public static Shape shape(EnumFacing side) {
        return side.getAxis() == EnumFacing.Axis.Y ? Shape.SLAB : Shape.VERTICAL_SLAB;
    }

    public static int orientation(EnumFacing side) {
        return side == EnumFacing.UP ? 0 : side == EnumFacing.DOWN ? 1 : side.getHorizontalIndex();
    }

    private static boolean centre(EnumFacing face, float hitX, float hitY, float hitZ) {
        double right = right(face, hitX, hitZ);
        double verticalHit = face.getAxis() == EnumFacing.Axis.Y ? hitZ : hitY;

        return right >= EDGE && right <= 1 - EDGE && verticalHit >= EDGE && verticalHit <= 1 - EDGE;
    }

    private static double right(EnumFacing face, float hitX, float hitZ) {
        switch (face.getAxis()) {
            case X:
                return face == EnumFacing.EAST ? hitZ : 1 - (double) hitZ;
            case Y:
                return face == EnumFacing.UP ? 1 - (double) hitX : hitX;
            default:
                return face == EnumFacing.SOUTH ? 1 - (double) hitX : hitX;
        }
    }

    private SlabPlacement() {
    }
}
