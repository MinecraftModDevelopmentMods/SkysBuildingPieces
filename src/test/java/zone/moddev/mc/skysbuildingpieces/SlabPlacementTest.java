package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.SlabPlacement;
import static org.junit.jupiter.api.Assertions.*;

class SlabPlacementTest {
    @Test void centresSitAgainstEveryClickedFace() {
        for(EnumFacing face:EnumFacing.values()) {
            assertEquals(face.getOpposite(),SlabPlacement.side(face,.5f,.5f,.5f));
            assertEquals(face.getAxis()==EnumFacing.Axis.Y?Shape.SLAB:Shape.VERTICAL_SLAB,
                    SlabPlacement.shape(SlabPlacement.side(face,.5f,.5f,.5f)));
        }
        assertEquals(1,SlabPlacement.orientation(EnumFacing.DOWN));
        assertEquals(0,SlabPlacement.orientation(EnumFacing.UP));
    }

    @Test void topAndBottomEdgesSelectAllFourVerticalHalves() {
        for(EnumFacing face:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN}) {
            assertEquals(EnumFacing.NORTH,SlabPlacement.side(face,.5f,.5f,.1f));
            assertEquals(EnumFacing.SOUTH,SlabPlacement.side(face,.5f,.5f,.9f));
            assertEquals(EnumFacing.WEST,SlabPlacement.side(face,.1f,.5f,.5f));
            assertEquals(EnumFacing.EAST,SlabPlacement.side(face,.9f,.5f,.5f));
        }
    }

    @Test void sideEdgesSelectHorizontalOrSidewaysHalves() {
        for(EnumFacing face:EnumFacing.HORIZONTALS) {
            assertEquals(EnumFacing.UP,SlabPlacement.side(face,.5f,.9f,.5f));
            assertEquals(EnumFacing.DOWN,SlabPlacement.side(face,.5f,.1f,.5f));
        }
        assertEquals(EnumFacing.WEST,SlabPlacement.side(EnumFacing.NORTH,.1f,.5f,.5f));
        assertEquals(EnumFacing.EAST,SlabPlacement.side(EnumFacing.SOUTH,.9f,.5f,.5f));
        assertEquals(EnumFacing.NORTH,SlabPlacement.side(EnumFacing.EAST,.5f,.5f,.1f));
        assertEquals(EnumFacing.SOUTH,SlabPlacement.side(EnumFacing.WEST,.5f,.5f,.9f));
    }

    @Test void legacyCentreBoundaryAndTieBreaksStayPredictable() {
        assertEquals(EnumFacing.DOWN,SlabPlacement.side(EnumFacing.UP,.5f,.5f,.22f));
        assertEquals(EnumFacing.NORTH,SlabPlacement.side(EnumFacing.UP,.5f,.5f,.219f));
        assertEquals(EnumFacing.EAST,SlabPlacement.side(EnumFacing.UP,.875f,.5f,.125f));
        assertEquals(EnumFacing.NORTH,SlabPlacement.side(EnumFacing.UP,.125f,.5f,.125f));
        assertEquals(EnumFacing.SOUTH,SlabPlacement.side(EnumFacing.UP,.125f,.5f,.875f));
        assertEquals(EnumFacing.EAST,SlabPlacement.verticalSide(EnumFacing.UP,.5f,.5f,.5f));
    }

    @Test void everyFaceAndClickHasExactlyOneRepresentableHalf() {
        for(EnumFacing face:EnumFacing.values())for(int a=0;a<=20;a++)for(int b=0;b<=20;b++) {
            float u=a/20f,v=b/20f;
            float x=face.getAxis()==EnumFacing.Axis.X?.5f:u;
            float y=face.getAxis()==EnumFacing.Axis.Y?.5f:v;
            float z=face.getAxis()==EnumFacing.Axis.X?u:face.getAxis()==EnumFacing.Axis.Y?v:.5f;
            EnumFacing side=SlabPlacement.side(face,x,y,z);
            int orientation=SlabPlacement.orientation(side);
            assertTrue(orientation>=0&&orientation<SlabPlacement.shape(side).states);
            assertEquals(SlabPlacement.shape(side)==Shape.SLAB,side.getAxis()==EnumFacing.Axis.Y);
            assertNotEquals(EnumFacing.Axis.Y,SlabPlacement.verticalSide(face,x,y,z).getAxis());
        }
    }
}
