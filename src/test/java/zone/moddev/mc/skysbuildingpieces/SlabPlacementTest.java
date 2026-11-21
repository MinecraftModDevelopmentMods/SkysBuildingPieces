package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.SlabPlacement;
import static org.junit.jupiter.api.Assertions.*;

class SlabPlacementTest {
    @Test
    void centresSitAgainstEveryClickedFace() {
        for (Direction face : Direction.values()) {
            assertEquals(face.getOpposite(), SlabPlacement.side(face, .5f, .5f, .5f));
            assertEquals(face.getAxis() == Direction.Axis.Y ? Shape.SLAB : Shape.VERTICAL_SLAB,
                    SlabPlacement.shape(SlabPlacement.side(face, .5f, .5f, .5f)));
        }

        assertEquals(1, SlabPlacement.orientation(Direction.DOWN));
        assertEquals(0, SlabPlacement.orientation(Direction.UP));
    }

    @Test
    void topAndBottomEdgesSelectAllFourVerticalHalves() {
        for (Direction face : new Direction[] { Direction.UP, Direction.DOWN }) {
            assertEquals(Direction.NORTH, SlabPlacement.side(face, .5f, .5f, .1f));
            assertEquals(Direction.SOUTH, SlabPlacement.side(face, .5f, .5f, .9f));
            assertEquals(Direction.WEST, SlabPlacement.side(face, .1f, .5f, .5f));
            assertEquals(Direction.EAST, SlabPlacement.side(face, .9f, .5f, .5f));
        }
    }

    @Test
    void sideEdgesSelectHorizontalOrSidewaysHalves() {
        for (Direction face : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST }) {
            assertEquals(Direction.UP, SlabPlacement.side(face, .5f, .9f, .5f));
            assertEquals(Direction.DOWN, SlabPlacement.side(face, .5f, .1f, .5f));
        }

        assertEquals(Direction.WEST, SlabPlacement.side(Direction.NORTH, .1f, .5f, .5f));
        assertEquals(Direction.EAST, SlabPlacement.side(Direction.SOUTH, .9f, .5f, .5f));
        assertEquals(Direction.NORTH, SlabPlacement.side(Direction.EAST, .5f, .5f, .1f));
        assertEquals(Direction.SOUTH, SlabPlacement.side(Direction.WEST, .5f, .5f, .9f));
    }

    @Test
    void legacyCentreBoundaryAndTieBreaksStayPredictable() {
        assertEquals(Direction.DOWN, SlabPlacement.side(Direction.UP, .5f, .5f, .22f));
        assertEquals(Direction.NORTH, SlabPlacement.side(Direction.UP, .5f, .5f, .219f));
        assertEquals(Direction.EAST, SlabPlacement.side(Direction.UP, .875f, .5f, .125f));
        assertEquals(Direction.NORTH, SlabPlacement.side(Direction.UP, .125f, .5f, .125f));
        assertEquals(Direction.SOUTH, SlabPlacement.side(Direction.UP, .125f, .5f, .875f));
        assertEquals(Direction.EAST, SlabPlacement.verticalSide(Direction.UP, .5f, .5f, .5f));
    }

    @Test
    void everyFaceAndClickHasExactlyOneRepresentableHalf() {
        for (Direction face : Direction.values()) {
            for (int a = 0; a <= 20; a++) {
                for (int b = 0; b <= 20; b++) {
                    float u = a / 20f;
                    float v = b / 20f;

                    float hitX = face.getAxis() == Direction.Axis.X ? .5f : u;
                    float hitY = face.getAxis() == Direction.Axis.Y ? .5f : v;
                    float hitZ = face.getAxis() == Direction.Axis.X ? u : face.getAxis() == Direction.Axis.Y ? v : .5f;
                    Direction side = SlabPlacement.side(face, hitX, hitY, hitZ);
                    int orientation = SlabPlacement.orientation(side);

                    assertTrue(orientation >= 0 && orientation < SlabPlacement.shape(side).states);
                    assertEquals(SlabPlacement.shape(side) == Shape.SLAB, side.getAxis() == Direction.Axis.Y);
                    assertNotEquals(Direction.Axis.Y, SlabPlacement.verticalSide(face, hitX, hitY, hitZ).getAxis());
                }
            }
        }
    }
}
