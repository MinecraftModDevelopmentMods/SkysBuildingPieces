package zone.moddev.mc.skysbuildingpieces;

import org.junit.jupiter.api.Test;
import java.util.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueTest {
    @Test void ordinaryVerticalDirtHasItsOwnAppendOnlyPalette() {
        Catalogue.Palette p=Catalogue.INSTANCE.palettes.get(Catalogue.INSTANCE.palettes.size()-1);
        assertEquals("dirt_vertical_slab_00",p.id);
        assertEquals(Shape.VERTICAL_SLAB,p.shape);
        assertEquals(Collections.singletonList("minecraft:dirt"),p.materials);
        for(int o=0;o<4;o++){assertEquals(o,p.meta("minecraft:dirt",o));assertEquals("minecraft:dirt",p.material(o));}
        assertThrows(IllegalArgumentException.class,()->p.material(4));
        assertTrue(LegacyMapping.owned("buildingbricks:dirt_vertical_slab"));
        assertTrue(LegacyMapping.allowedMaterial("minecraft:dirt",Shape.VERTICAL_SLAB));
        assertFalse(LegacyMapping.allowedMaterial("minecraft:dirt",Shape.SLAB));
        assertFalse(LegacyMapping.allowedMaterial("minecraft:grass",Shape.VERTICAL_SLAB));
        assertFalse(LegacyMapping.allowedMaterial("minecraft:dirt_coarse",Shape.VERTICAL_SLAB));
    }

    @Test void paletteIdentitiesAndSlotsAreChecksumLocked() throws Exception {
        byte[] bytes=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/skysbuildingpieces/catalogue/palettes.json"));
        StringBuilder sha=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))sha.append(String.format("%02X",b));
        assertEquals("4E1884F8B5B050650B34EB836CB7C2B3789305B0E281C21D187BE76D33089E12",sha.toString());
        com.google.gson.JsonArray all=new com.google.gson.JsonParser().parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
        com.google.gson.JsonArray original=new com.google.gson.JsonArray();for(int i=0;i<234;i++)original.add(all.get(i));
        sha.setLength(0);for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(original.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))sha.append(String.format("%02X",b));
        assertEquals("2447AD1A544FE48347AFAC80FFE13602FFBC2907FC9C37E78D81F49F5B678D67",sha.toString(),"original palettes and slots remain unchanged");
    }
    @Test void materialPropertiesAreDefinedForEveryVariant() {
        for(Catalogue.MaterialDef material:Catalogue.INSTANCE.materials.values()) {
            assertTrue(Float.isFinite(material.hardness()));assertTrue(material.hardness()>=0);
            assertTrue(Float.isFinite(material.resistance()));assertNotNull(material.normalDrop());
        }
        assertEquals(.6f,Catalogue.INSTANCE.materials.get("minecraft:grass").hardness());
    }
    @Test void permanentCatalogueAndBudget() {
        Catalogue c=Catalogue.INSTANCE;assertEquals(102,c.materials.size());assertEquals(235,c.palettes.size());assertEquals(236,c.palettes.size()+1);assertTrue(c.palettes.size()+1<=256);
        Set<String> coverage=new HashSet<String>();
        for(Catalogue.Palette p:c.palettes)for(String material:p.materials) {
            assertTrue(coverage.add(p.shape+material));
            assertTrue(Catalogue.supportsShape(c.materials.get(material),p.shape));
            for(int o=0;o<p.shape.states;o++){int meta=p.meta(material,o);assertTrue(meta>=0&&meta<16);assertEquals(material,p.material(meta));assertEquals(o,meta%p.shape.states);}
        }
    }
    @Test void exhaustiveGeometry() {
        for(Shape shape:Shape.values())if(shape!=Shape.PANE&&shape!=Shape.WALL) {
            Set<Integer> masks=new HashSet<Integer>();
            for(int o=0;o<shape.states;o++) {
                int m=Geometry.mask(shape,o);assertTrue(m>0&&m<255);assertTrue(masks.add(m));assertEquals(o,Geometry.orientationFor(shape,m));
                int count=shape==Shape.SLAB||shape==Shape.VERTICAL_SLAB?4:shape==Shape.CORNER?1:shape==Shape.STAIRS?6:2;
                assertEquals(count,Integer.bitCount(m));
                assertEquals((m&240)==240,Geometry.completeTop(m));
            }
        }
    }
    @Test void legacyGeometryAllCodes() {
        for(int meta=0;meta<16;meta++) {
            Shape shape=LegacyMapping.shape("buildingbricks:rock_step",meta);
            int o=LegacyMapping.orientation(shape,meta);
            assertEquals(Geometry.mask(Shape.STEP,meta<8?meta:8+(meta&3)),Geometry.mask(shape,o));
        }
        assertTrue(LegacyMapping.owned("buildingbricks:dirt_vertical_slab"));
        assertFalse(LegacyMapping.owned("buildingbricks:grass_slab"));
        assertFalse(LegacyMapping.owned("skysgrassslabs:grass_slab"));
        assertTrue(LegacyMapping.owned("buildingbricks:rock_step"));
    }
    @Test void arbitrationIsRestartGatedAndIndependent() {
        assertFalse(SkysBuildingPieces.forceReplace);
        assertFalse(LegacyMapping.enabled(true,false));assertTrue(LegacyMapping.enabled(true,true));
        assertTrue(LegacyMapping.enabled(false,false));assertTrue(LegacyMapping.enabled(false,true));
    }
}
