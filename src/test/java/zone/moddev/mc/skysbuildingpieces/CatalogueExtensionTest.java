package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueExtensionTest {
    private JsonArray materials() {
        return new JsonParser().parse(
                "[{\"id\":\"example:wood\",\"name\":\"Wood\",\"type\":\"wood\",\"group\":\"wood\",\"blocks\":{\"full\":\"minecraft:planks\"},\"flammability\":0,\"fireSpread\":0}]")
                .getAsJsonArray();
    }

    private JsonArray palettes() {
        return new JsonParser().parse(
                "[{\"id\":\"wood_corner_00\",\"group\":\"wood\",\"shape\":\"corner\",\"materials\":[\"example:wood\"]}]")
                .getAsJsonArray();
    }

    @Test
    void modulesAreIndependentAndDefinitionsImmutable() {
        Catalogue catalogue = new Catalogue();
        JsonArray input = materials();
        Catalogue.Module catalogueModule = catalogue.registerModule("example", "examplepieces", input, palettes(), 1);

        assertEquals(235, catalogue.modules.get("vanilla").palettes.size());
        assertEquals(236, catalogue.palettes.size());
        assertEquals("examplepieces:wood_corner_00", catalogueModule.palettes.get(0).registryId());
        assertEquals("example", catalogue.moduleFor("example:wood"));
        input.get(0).getAsJsonObject().addProperty("flammability", 20);
        assertEquals(0, catalogueModule.materials.get("example:wood").flammability());
        assertEquals(0, catalogueModule.materials.get("example:wood").fireSpread());
        assertThrows(UnsupportedOperationException.class, () -> catalogueModule.palettes.get(0).materials.add("example:other"));
        assertThrows(UnsupportedOperationException.class, () -> catalogue.materials.clear());
    }

    @Test
    void duplicateLateAndInvalidContributionsFailAtomically() {
        Catalogue catalogue = new Catalogue();
        JsonArray bad = palettes();

        bad.get(0).getAsJsonObject().getAsJsonArray("materials").add(new JsonPrimitive("example:missing"));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.registerModule("example", "examplepieces", materials(), bad, 1));
        assertEquals(102, catalogue.materials.size());
        assertEquals(235, catalogue.palettes.size());
        catalogue.registerModule("example", "examplepieces", materials(), palettes(), 1);
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.registerModule("example", "another", materials(), palettes(), 1));
        catalogue.freeze();
        assertThrows(IllegalStateException.class,
                () -> catalogue.registerModule("later", "laterpieces", materials(), palettes(), 1));
    }

    @Test
    void budgetAndRepeatedSlotsAreRejected() {
        Catalogue catalogue = new Catalogue();
        JsonArray doubled = palettes();

        doubled.add(doubled.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.registerModule("example", "examplepieces", materials(), doubled, 1));

        JsonArray repeated = palettes();

        repeated.get(0).getAsJsonObject().getAsJsonArray("materials").add(new JsonPrimitive("example:wood"));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.registerModule("example", "examplepieces", materials(), repeated, 1));
        assertEquals(235, catalogue.palettes.size());
    }
}
