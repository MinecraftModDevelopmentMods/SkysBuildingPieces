package zone.moddev.mc.skysbuildingpieces.content;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

class PaletteItemIdsTest {
    @Test
    void everyMaterialHasOneDistinctItemForItsShape() {
        Set<String> inventoryIds = new HashSet<>();
        Set<String> paletteIds = new HashSet<>();

        for (Catalogue.Palette palette : Catalogue.INSTANCE.palettes) {
            paletteIds.add(palette.registryId());

            for (String material : palette.materials) {
                assertTrue(inventoryIds.add(PaletteItemIds.itemId(palette, material)),
                        "Two materials share an inventory identity");
            }
        }

        assertTrue(inventoryIds.stream().noneMatch(paletteIds::contains),
                "A normal item would overwrite an old palette recovery item");
    }

    @Test
    void oldItemOrientationsKeepTheirMaterialAndReservedSlotsAreRejected() {
        for (Catalogue.Palette palette : Catalogue.INSTANCE.palettes) {
            int assigned = palette.materials.size() * palette.shape.states;

            for (int metadata = 0; metadata < assigned; metadata++) {
                assertEquals(PaletteItemIds.itemId(palette, palette.material(metadata)),
                        PaletteItemIds.fromLegacyMetadata(palette, metadata));
            }
            for (int reserved = assigned; reserved < 16; reserved++) {
                final int metadata = reserved;

                assertThrows(IllegalArgumentException.class,
                        () -> PaletteItemIds.fromLegacyMetadata(palette, metadata));
            }

            assertThrows(IllegalArgumentException.class, () -> PaletteItemIds.fromLegacyMetadata(palette, -1));
            assertThrows(IllegalArgumentException.class, () -> PaletteItemIds.fromLegacyMetadata(palette, 16));
            assertThrows(IllegalArgumentException.class, () -> PaletteItemIds.itemId(palette, "fixture:unknown"));
        }
    }
}
