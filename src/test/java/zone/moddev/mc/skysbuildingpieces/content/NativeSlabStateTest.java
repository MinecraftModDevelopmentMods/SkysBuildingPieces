package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSlabStateTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap.registerVanilla();
    }

    @Test
    void directionalSlabsPointTowardTheirExposedFace() {
        assertEquals(Direction.DOWN, NativeSlabState.facingFor(0));
        assertEquals(Direction.UP, NativeSlabState.facingFor(1));
    }

    @Test
    void vanillaOrientationsKeepWaterAndMaterial() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
        BlockState upper = NativeSlabState.orient(slab, 0);
        BlockState lower = NativeSlabState.orient(upper, 1);

        assertEquals(SlabType.TOP, upper.getValue(SlabBlock.TYPE));
        assertEquals(SlabType.BOTTOM, lower.getValue(SlabBlock.TYPE));
        assertTrue(lower.getValue(SlabBlock.WATERLOGGED));
        assertSame(Blocks.OAK_SLAB, lower.getBlock());
    }
}
