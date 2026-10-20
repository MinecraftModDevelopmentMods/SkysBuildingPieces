package zone.moddev.mc.skysbuildingpieces.content;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import static org.junit.jupiter.api.Assertions.*;

class PieceNamesTest {
    @Test
    void everyLocaleFormatsEveryMaterialAndShape() throws Exception {
        Map<String, String> goldWalls = new HashMap<String, String>();

        goldWalls.put("en", "Gold Wall");
        goldWalls.put("de", "Mauer aus Gold");
        goldWalls.put("es", "Muro de oro");
        goldWalls.put("fr", "Muret en or");
        goldWalls.put("ja", "金の塀");
        goldWalls.put("ko", "금 담장");
        goldWalls.put("pt", "Muro de ouro");
        goldWalls.put("ru", "Ограда из золота");
        goldWalls.put("zh", "金墙");

        Path root = Paths.get("src/main/resources/assets/skysbuildingpieces/lang");

        try (java.util.stream.Stream<Path> files = Files.list(root)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Properties names = new Properties();

                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    names.load(reader);
                }

                java.util.function.Function<String, String> translate = key -> names.getProperty(key, key);

                assertEquals(goldWalls.get(file.getFileName().toString().substring(0, 2)), PieceNames.format(
                        "skysbuildingpieces", "minecraft:gold_block", "wall", "WRONG FULL BLOCK LABEL", translate));

                for (String material : Catalogue.INSTANCE.materials.keySet()) {
                    for (Shape shape : Shape.values()) {
                        String result = PieceNames.format("skysbuildingpieces", material,
                                shape.name().toLowerCase(Locale.ROOT), "WRONG FULL BLOCK LABEL", translate);

                        assertFalse(result.contains("WRONG"));
                        assertFalse(result.contains("%"));
                        assertFalse(result.contains("material."));
                        assertFalse(result.contains("pieces."));
                        assertEquals(result.trim(), result);
                        assertFalse(result.isEmpty());
                    }
                }
            }
        }
    }

    @Test
    void unknownAddonsKeepTheirNativeNameAndFallbackShape() {
        Map<String, String> names = new HashMap<String, String>();

        names.put("pieces.skysbuildingpieces.wall", "%s Wall");
        names.put("material.skysbuildingpieces.minecraft.gold_block", "Gold");

        java.util.function.Function<String, String> translate = key -> names.getOrDefault(key, key);

        assertEquals("Future Alloy Wall",
                PieceNames.format("future_addon", "othermod:gold_block", "wall", "Future Alloy", translate));
        names.put("pieces.future_addon.wall", "Wall of %s");
        names.put("material.future_addon.othermod.gold_block", "Future Alloy");
        assertEquals("Wall of Future Alloy",
                PieceNames.format("future_addon", "othermod:gold_block", "wall", "WRONG", translate));
    }

    @Test
    void malformedResourcePackFormatsKeepMinecraftErrorHandling() {
        Map<String, String> names = new HashMap<String, String>();

        names.put("pieces.skysbuildingpieces.wall", "%d Wall");
        assertEquals("Format error: %d Wall", PieceNames.format("skysbuildingpieces", "minecraft:gold_block", "wall",
                "Block of Gold", key -> names.getOrDefault(key, key)));
    }
}
