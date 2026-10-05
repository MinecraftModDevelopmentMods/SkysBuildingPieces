package zone.moddev.mc.skysbuildingpieces;

import org.junit.jupiter.api.Test;
import com.google.gson.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceTest {
    private static final Path ROOT=Paths.get("src/main/resources/assets/skysbuildingpieces");
    @Test void allEighteenUtf8LocalesMatch() throws Exception {
        List<String> expected=Arrays.asList("de_AT","de_AU","de_DE","en_CA","en_EN","en_GB","en_PT","en_US","es_ES","es_MX","fr_CA","fr_FR","ja_JP","ko_KR","pt_BR","pt_PT","ru_RU","zh_CN");
        Set<String> actual=new TreeSet<String>();try(java.util.stream.Stream<Path> files=Files.list(ROOT.resolve("lang"))){files.forEach(p->actual.add(p.getFileName().toString().replace(".lang","")));}assertEquals(new TreeSet<String>(expected),actual);
        List<String> keys=null;
        for(String locale:expected) {
            byte[] bytes=Files.readAllBytes(ROOT.resolve("lang/"+locale+".lang"));
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            assertFalse(text.startsWith("\uFEFF"));assertFalse(text.contains("\r"));assertFalse(text.contains("\uFFFD"));assertTrue(text.endsWith("\n"));
            List<String> current=new ArrayList<String>();for(String line:text.split("\n")){assertEquals(line.trim(),line);assertTrue(line.contains("="));String key=line.substring(0,line.indexOf('='));assertFalse(current.contains(key));current.add(key);assertTrue(line.substring(line.indexOf('=')+1).length()>0);}
            assertEquals(16,current.size());if(keys==null)keys=current;else assertEquals(keys,current);
        }
        assertArrayEquals(Files.readAllBytes(ROOT.resolve("lang/de_DE.lang")),Files.readAllBytes(ROOT.resolve("lang/de_AU.lang")));
        for(String english:Arrays.asList("en_CA","en_EN","en_GB","en_PT"))assertArrayEquals(Files.readAllBytes(ROOT.resolve("lang/en_US.lang")),Files.readAllBytes(ROOT.resolve("lang/"+english+".lang")));
    }
    @Test void catalogueHasCompleteModelsAndNoCulledInternalFaces() throws Exception {
        for(Catalogue.Palette p:Catalogue.INSTANCE.palettes) {
            JsonObject blockstate=json(ROOT.resolve("blockstates/"+p.id+".json"));assertTrue(blockstate.has("variants"));
            for(Map.Entry<String,JsonElement> entry:blockstate.getAsJsonObject("variants").entrySet()) {
                String name=entry.getValue().getAsJsonObject().get("model").getAsString().substring("skysbuildingpieces:".length());
                assertTrue(Files.isRegularFile(ROOT.resolve("models/block/"+name+".json")),name);
            }
            for(String m:p.materials)assertTrue(Files.isRegularFile(ROOT.resolve("models/item/"+m.substring(10)+"_"+p.shape.name().toLowerCase(Locale.ROOT)+".json")));
        }
        try(java.util.stream.Stream<Path> models=Files.list(ROOT.resolve("models/block"))) {
            for(Path model:(Iterable<Path>)models::iterator) {
                JsonObject data=json(model);assertTrue(data.has("textures"));
                for(JsonElement element:data.getAsJsonArray("elements"))for(Map.Entry<String,JsonElement> f:element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    assertFalse(f.getValue().getAsJsonObject().has("cullface"),model.toString());
                    if(model.getFileName().toString().contains("_snow"))assertFalse(f.getValue().getAsJsonObject().has("tintindex"));
                }
            }
        }
    }
    @Test void templateModelsAreDistinct() throws Exception {
        Set<String> models=new HashSet<String>();for(String t:Arrays.asList("slab","vertical_slab","step","corner","stairs","wall","pane"))assertTrue(models.add(new String(Files.readAllBytes(ROOT.resolve("models/item/template_"+t+".json")),StandardCharsets.UTF_8)));
    }
    private static JsonObject json(Path p)throws Exception {return new JsonParser().parse(new String(Files.readAllBytes(p),StandardCharsets.UTF_8)).getAsJsonObject();}
}
