package zone.moddev.mc.skysbuildingpieces;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import static org.junit.jupiter.api.Assertions.*;

class PieceNameTest {
    @Test
    void everyMaterialHasASeparateTranslatedPieceName() throws Exception {
        Path root = Paths.get("src/main/resources/assets/skysbuildingpieces/lang");

        try (java.util.stream.Stream<Path> files = Files.list(root)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Properties names = read(file);

                for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
                    assertNotNull(names.getProperty("material.skysbuildingpieces." + material.id.replace(':', '.')),
                            file + " / " + material.id);
                }
            }
        }
    }

    @Test
    void englishUsesTheMaterialRatherThanTheFullBlockLabel() throws Exception {
        Path root = Paths.get("src/main/resources/assets/skysbuildingpieces/lang");
        Path english = Files.exists(root.resolve("en_US.lang")) ? root.resolve("en_US.lang")
                : root.resolve("en_us.lang");
        Properties names = read(english);

        assertEquals("Gold Wall", name(names, "gold_block", "wall"));
        assertEquals("Gold Stairs", name(names, "gold_block", "stairs"));
        assertEquals("Iron Slab", name(names, "iron_block", "slab"));
        assertEquals("Oak Step", name(names, "planks_oak", "step"));
        assertEquals("Smooth Stone Wall", name(names, "stone_slab", "wall"));
        assertEquals("Purpur Corner", name(names, "purpur_block", "corner"));
    }

    private static String name(Properties names, String material, String shape) {
        return String.format(Locale.ROOT, names.getProperty("pieces.skysbuildingpieces." + shape),
                names.getProperty("material.skysbuildingpieces.minecraft." + material));
    }

    private static Properties read(Path file) throws Exception {
        Properties result = new Properties();

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            result.load(reader);
        }

        return result;
    }
}
