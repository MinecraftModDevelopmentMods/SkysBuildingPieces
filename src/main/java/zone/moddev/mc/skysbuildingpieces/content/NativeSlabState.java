package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.state.Property;
import net.minecraft.state.properties.SlabType;
import net.minecraft.util.Direction;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Vanilla halves and Mineralogy's six-way facing describe the same occupied space. */
final class NativeSlabState {
    private NativeSlabState() {
    }

    static Direction facingFor(int orientation) {
        return orientation == 0 ? Direction.DOWN : Direction.UP;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static BlockState orient(BlockState nativeState, int orientation) {
        if (nativeState.hasProperty(SlabBlock.TYPE)) {
            return nativeState.setValue(SlabBlock.TYPE, orientation == 0 ? SlabType.TOP : SlabType.BOTTOM);
        }
        for (Property property : nativeState.getProperties()) {
            if (directional(property)) {
                return nativeState.setValue(property, facingFor(orientation));
            }
        }

        throw new IllegalArgumentException("No supported slab orientation on " + nativeState);
    }

    static int mask(BlockState state) {
        if (state.hasProperty(SlabBlock.TYPE)) {
            SlabType half = state.getValue(SlabBlock.TYPE);

            return half == SlabType.DOUBLE ? 255 : Geometry.mask(Shape.SLAB, half == SlabType.TOP ? 0 : 1);
        }
        for (Property<?> property : state.getProperties()) {
            if (directional(property)) {
                Direction outward = (Direction) state.getValue(property);

                if (outward.getAxis() == Direction.Axis.Y) {
                    return Geometry.mask(Shape.SLAB, outward == Direction.DOWN ? 0 : 1);
                }

                return Geometry.mask(Shape.VERTICAL_SLAB, outward.getOpposite().get2DDataValue());
            }
        }

        return -1;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static BlockState vertical(BlockState state, int orientation) {
        Direction occupied = new Direction[] { Direction.SOUTH, Direction.WEST, Direction.NORTH,
                Direction.EAST }[orientation & 3];

        for (Property property : state.getProperties()) {
            if (directional(property) && property.getPossibleValues().contains(occupied.getOpposite())) {
                return state.setValue(property, occupied.getOpposite());
            }
        }

        return null;
    }

    private static boolean directional(Property<?> property) {
        return property.getName().equals("facing") && property.getPossibleValues().contains(Direction.UP)
                && property.getPossibleValues().contains(Direction.DOWN);
    }
}
