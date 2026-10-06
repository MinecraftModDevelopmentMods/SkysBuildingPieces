package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueExtensionTest {
    private JsonArray materials() {
        return new JsonParser().parse("[{\"id\":\"example:wood\",\"name\":\"Wood\",\"type\":\"wood\",\"group\":\"wood\",\"blocks\":{\"full\":\"minecraft:planks\"},\"flammability\":0,\"fireSpread\":0}]").getAsJsonArray();
    }
    private JsonArray palettes() {
        return new JsonParser().parse("[{\"id\":\"wood_corner_00\",\"group\":\"wood\",\"shape\":\"corner\",\"materials\":[\"example:wood\"]}]").getAsJsonArray();
    }
    @Test void modulesAreIndependentAndDefinitionsImmutable() {
        Catalogue c=new Catalogue();JsonArray input=materials();
        Catalogue.Module m=c.registerModule("example","examplepieces",input,palettes(),1);
        assertEquals(235,c.modules.get("vanilla").palettes.size());assertEquals(236,c.palettes.size());
        assertEquals("examplepieces:wood_corner_00",m.palettes.get(0).registryId());
        assertEquals("example",c.moduleFor("example:wood"));
        input.get(0).getAsJsonObject().addProperty("flammability",20);
        assertEquals(0,m.materials.get("example:wood").flammability());
        assertEquals(0,m.materials.get("example:wood").fireSpread());
        assertThrows(UnsupportedOperationException.class,()->m.palettes.get(0).materials.add("example:other"));
        assertThrows(UnsupportedOperationException.class,()->c.materials.clear());
    }
    @Test void duplicateLateAndInvalidContributionsFailAtomically() {
        Catalogue c=new Catalogue();JsonArray bad=palettes();bad.get(0).getAsJsonObject().getAsJsonArray("materials").add(new JsonPrimitive("example:missing"));
        assertThrows(IllegalArgumentException.class,()->c.registerModule("example","examplepieces",materials(),bad,1));
        assertEquals(102,c.materials.size());assertEquals(235,c.palettes.size());
        c.registerModule("example","examplepieces",materials(),palettes(),1);
        assertThrows(IllegalArgumentException.class,()->c.registerModule("example","another",materials(),palettes(),1));
        c.freeze();assertThrows(IllegalStateException.class,()->c.registerModule("later","laterpieces",materials(),palettes(),1));
    }
    @Test void budgetAndRepeatedSlotsAreRejected() {
        Catalogue c=new Catalogue();JsonArray doubled=palettes();doubled.add(doubled.get(0));
        assertThrows(IllegalArgumentException.class,()->c.registerModule("example","examplepieces",materials(),doubled,1));
        JsonArray repeated=palettes();repeated.get(0).getAsJsonObject().getAsJsonArray("materials").add(new JsonPrimitive("example:wood"));
        assertThrows(IllegalArgumentException.class,()->c.registerModule("example","examplepieces",materials(),repeated,1));
        assertEquals(235,c.palettes.size());
    }
}
