package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.util.Direction;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Face-local centre/edge selection, independent of any other mod's placement handlers. */
public final class SlabPlacement {
    private static final double EDGE = .22f;

    /** The half of the destination block occupied by a slab. */
    public static Direction side(Direction face, float hitX, float hitY, float hitZ) {
        if (centre(face, hitX, hitY, hitZ)) {
            return face.getOpposite();
        }
        if (face.getAxis() == Direction.Axis.Y) {
            if (hitX >= hitZ) {
                return hitX < 1 - (double) hitZ ? Direction.NORTH : Direction.EAST;
            }

            return hitX >= 1 - (double) hitZ ? Direction.SOUTH : Direction.WEST;
        }

        double right = right(face, hitX, hitZ);

        if (right >= hitY) {
            return right < 1 - (double) hitY ? Direction.DOWN : face.getClockWise();
        }

        return right >= 1 - (double) hitY ? Direction.UP : face.getCounterClockWise();
    }

    /** Explicit vertical-slab items stay vertical, including clicks near a side edge. */
    public static Direction verticalSide(Direction face, float hitX, float hitY, float hitZ) {
        if (face.getAxis() == Direction.Axis.Y) {
            if (hitX >= hitZ) {
                return hitX < 1 - (double) hitZ ? Direction.NORTH : Direction.EAST;
            }

            return hitX >= 1 - (double) hitZ ? Direction.SOUTH : Direction.WEST;
        }
        if (centre(face, hitX, hitY, hitZ)) {
            return face.getOpposite();
        }

        return right(face, hitX, hitZ) >= hitY ? face.getClockWise() : face.getCounterClockWise();
    }

    public static Shape shape(Direction side) {
        return side.getAxis() == Direction.Axis.Y ? Shape.SLAB : Shape.VERTICAL_SLAB;
    }

    public static int orientation(Direction side) {
        return side == Direction.UP ? 0 : side == Direction.DOWN ? 1 : side.get2DDataValue();
    }

    private static boolean centre(Direction face, float hitX, float hitY, float hitZ) {
        double right = right(face, hitX, hitZ);
        double verticalHit = face.getAxis() == Direction.Axis.Y ? hitZ : hitY;

        return right >= EDGE && right <= 1 - EDGE && verticalHit >= EDGE && verticalHit <= 1 - EDGE;
    }

    private static double right(Direction face, float hitX, float hitZ) {
        switch (face.getAxis()) {
            case X:
                return face == Direction.EAST ? hitZ : 1 - (double) hitZ;
            case Y:
                return face == Direction.UP ? 1 - (double) hitX : hitX;
            default:
                return face == Direction.SOUTH ? 1 - (double) hitX : hitX;
        }
    }

    private SlabPlacement() {
    }
}
