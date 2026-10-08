package zone.moddev.mc.skysbuildingpieces;

import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.content.CraftingPattern;
import static org.junit.jupiter.api.Assertions.*;

class CraftingPatternTest {
    @Test void rowsAndColumnsHaveDifferentShapeAndVanillaQuantities() {
        assertEquals(3, CraftingPattern.SLAB.width()); assertEquals(1, CraftingPattern.SLAB.height());
        assertEquals(1, CraftingPattern.VERTICAL_SLAB.width()); assertEquals(3, CraftingPattern.VERTICAL_SLAB.height());
        for (CraftingPattern pattern : new CraftingPattern[]{CraftingPattern.SLAB, CraftingPattern.VERTICAL_SLAB}) {
            assertEquals(3, pattern.ingredients()); assertEquals(6, pattern.yield(false)); assertEquals(3, pattern.yield(true));
        }
    }
    @Test void quartersAndEighthsConserveMaterial() {
        assertEquals(2, CraftingPattern.STEP.ingredients()); assertEquals(4, CraftingPattern.STEP.yield(false));
        assertEquals(2, CraftingPattern.SOIL_STEP.ingredients()); assertEquals(8, CraftingPattern.SOIL_STEP.yield(false));
        assertTrue(CraftingPattern.STEP.occupied(0, 0)); assertTrue(CraftingPattern.STEP.occupied(1, 1));
        assertFalse(CraftingPattern.STEP.occupied(1, 0)); assertFalse(CraftingPattern.STEP.occupied(0, 1));
        assertEquals(2, CraftingPattern.CORNER.ingredients()); assertEquals(4, CraftingPattern.CORNER.yield(false));
        assertEquals(CraftingPattern.STEP.ingredients() / 2.0, CraftingPattern.STEP.yield(false) / 4.0);
        assertEquals(CraftingPattern.SOIL_STEP.ingredients(), CraftingPattern.SOIL_STEP.yield(false) / 4);
        assertEquals(CraftingPattern.CORNER.ingredients() / 4.0, CraftingPattern.CORNER.yield(false) / 8.0);
    }
    @Test void wallsAndPanesCannotMatchTheSamePattern() {
        assertEquals(2, CraftingPattern.WALL.width()); assertEquals(3, CraftingPattern.WALL.height());
        assertEquals(3, CraftingPattern.PANE.width()); assertEquals(2, CraftingPattern.PANE.height());
        assertEquals(6, CraftingPattern.WALL.ingredients()); assertEquals(6, CraftingPattern.WALL.yield(false));
        assertEquals(6, CraftingPattern.PANE.ingredients()); assertEquals(16, CraftingPattern.PANE.yield(false));
        assertEquals(6, CraftingPattern.STAIRS.ingredients()); assertEquals(4, CraftingPattern.STAIRS.yield(false));
    }
    @Test void rotationIsOneForOne() {
        assertEquals(1, CraftingPattern.ROTATE.ingredients()); assertEquals(1, CraftingPattern.ROTATE.yield(false));
    }
    @Test void nativeWildcardCutsKeepTheirOwnResults() {
        for (String material : new String[]{"chiseled_sandstone", "smooth_sandstone", "chiseled_red_sandstone",
                "smooth_red_sandstone", "stonebrick_carved", "stonebrick_cracked", "stonebrick_mossy"}) {
            assertTrue(CraftingPattern.wildcardNativeCuts("minecraft:" + material));
            assertTrue(CraftingPattern.rotateOnlySlab("minecraft:" + material));
        }
        assertTrue(CraftingPattern.rotateOnlySlab("minecraft:stone"));
        assertTrue(CraftingPattern.rotateOnlySlab("minecraft:snow"));
        assertFalse(CraftingPattern.wildcardNativeCuts("minecraft:stone"));
        assertFalse(CraftingPattern.rotateOnlySlab("minecraft:granite"));
        assertFalse(CraftingPattern.rotateOnlySlab("biomesoplenty:marble"));
    }
}
