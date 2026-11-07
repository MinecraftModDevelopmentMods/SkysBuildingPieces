package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCatalogueContractTest {
    @Test
    void everySourceIdentityKeepsItsExactPreFlatteningDefinition() throws Exception {
        JsonObject sources;

        try (InputStreamReader input = new InputStreamReader(
                getClass().getResourceAsStream("/contracts/catalogues-1.12.json"), StandardCharsets.UTF_8)) {
            sources = new JsonParser().parse(input).getAsJsonObject();
        }

        int checked = 0;

        for (Map.Entry<String, JsonElement> source : sources.entrySet()) {
            JsonArray target = new JsonParser().parse(new String(
                    Files.readAllBytes(
                            Paths.get("src/main/resources/assets/" + source.getKey() + "/catalogue/materials.json")),
                    StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String, JsonObject> definitions = new LinkedHashMap<>();

            for (JsonElement entry : target) {
                JsonObject definition = entry.getAsJsonObject();

                assertNull(definitions.put(definition.get("id").getAsString(), definition));
            }

            assertEquals(source.getValue().getAsJsonArray().size(), definitions.size());

            for (JsonElement entry : source.getValue().getAsJsonArray()) {
                JsonObject original = entry.getAsJsonObject();
                String id = original.get("id").getAsString();
                JsonObject nativeDefinition = definitions.get(id);

                assertNotNull(nativeDefinition, id);
                assertEquals(original.get("blocks"), nativeDefinition.get("legacy_blocks"), id);
                assertEquals(original.get("textures"), nativeDefinition.get("legacy_textures"), id);

                for (String retained : new String[] { "group", "type", "normalHarvest", "fullBlockStepRecipe" }) {
                    assertEquals(original.get(retained), nativeDefinition.get(retained), id + " / " + retained);
                }

                checked++;
            }
        }

        assertEquals(130, checked);
    }
}
