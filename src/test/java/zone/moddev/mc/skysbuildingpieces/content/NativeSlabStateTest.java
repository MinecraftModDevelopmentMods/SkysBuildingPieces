package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSlabStateTest {
    @Test
    void directionalSlabsPointTowardTheirExposedFace() {
        assertEquals(EnumFacing.DOWN, NativeSlabState.facingFor(0));
        assertEquals(EnumFacing.UP, NativeSlabState.facingFor(1));
    }

    @Test
    void vanillaSlabsKeepTheirMaterialAndCanReturnToEitherHalf() {
        for (int material = 0; material < 8; material++) {
            assertEquals(material | 8, NativeSlabState.vanillaMetadata(material, 0));
            assertEquals(material, NativeSlabState.vanillaMetadata(material | 8, 1));
        }
    }
}
