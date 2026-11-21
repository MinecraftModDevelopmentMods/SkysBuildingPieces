package zone.moddev.mc.skysbuildingpieces;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

final class CatalogueReplacementTest {
    @Test
    void replacementUsesAnExistingMaterialWithoutChangingTheSavedSlot() {
        Catalogue catalogue = new Catalogue();

        assertFalse(catalogue.hasReplacements());

        JsonArray materials = new JsonArray();

        materials.add(material("test:retired", "minecraft:planks_oak"));
        catalogue.registerModule("retired_test", "retiredtest", materials, palette("test:retired"), 1);
        assertTrue(catalogue.hasReplacements());
        assertEquals("minecraft:planks_oak", catalogue.resolveReplacement("test:retired"));
        assertEquals("test:retired", catalogue.modules.get("retired_test").palettes.get(0).material(7));
        assertEquals("retiredtest:wood_corner_00", catalogue.modules.get("retired_test").palettes.get(0).registryId());
        assertEquals("minecraft:planks_oak", catalogue.resolveReplacement("minecraft:planks_oak"));
    }

    @Test
    void missingTargetRejectsTheWholeContribution() {
        Catalogue catalogue = new Catalogue();
        int before = catalogue.materials.size();
        JsonArray materials = new JsonArray();

        materials.add(material("test:retired", "missing:wood"));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.registerModule("retired_test", "retiredtest", materials, palette("test:retired"), 1));
        assertEquals(before, catalogue.materials.size());
        assertFalse(catalogue.modules.containsKey("retired_test"));
        assertFalse(catalogue.hasReplacements());
    }

    @Test
    void cyclesCannotPublishAnyMaterialsOrPalettes() {
        Catalogue catalogue = new Catalogue();
        JsonArray materials = new JsonArray();

        materials.add(material("test:first", "test:second"));
        materials.add(material("test:second", "test:first"));
        assertThrows(IllegalArgumentException.class, () -> catalogue.registerModule("retired_test", "retiredtest",
                materials, palette("test:first", "test:second"), 1));
        assertFalse(catalogue.materials.containsKey("test:first"));
        assertFalse(catalogue.materials.containsKey("test:second"));
        assertFalse(catalogue.modules.containsKey("retired_test"));
    }

    @Test
    void chainedReplacementsResolveBeforeTheCatalogueFreezes() {
        Catalogue catalogue = new Catalogue();
        JsonArray materials = new JsonArray();

        materials.add(material("test:first", "test:second"));
        materials.add(material("test:second", "minecraft:planks_oak"));
        catalogue.registerModule("retired_test", "retiredtest", materials, palette("test:first", "test:second"), 1);
        catalogue.freeze();
        assertEquals("minecraft:planks_oak", catalogue.resolveReplacement("test:first"));
    }

    private static JsonObject material(String identity, String replacement) {
        JsonObject result = new JsonObject();

        result.addProperty("id", identity);
        result.addProperty("name", "Retired Wood");
        result.addProperty("type", "wood");
        result.addProperty("group", "wood");
        result.addProperty("replacement", replacement);

        JsonObject blocks = new JsonObject();

        blocks.addProperty("full", "minecraft:oak_planks");
        result.add("blocks", blocks);
        return result;
    }

    private static JsonArray palette(String... materials) {
        JsonObject palette = new JsonObject();

        palette.addProperty("id", "wood_corner_00");
        palette.addProperty("group", "wood");
        palette.addProperty("shape", "corner");
        palette.addProperty("states", 8);

        JsonArray slots = new JsonArray();

        for (String material : materials) {
            slots.add(material);
        }

        palette.add("materials", slots);

        JsonArray result = new JsonArray();

        result.add(palette);
        return result;
    }
}
