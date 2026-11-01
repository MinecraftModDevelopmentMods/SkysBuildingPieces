package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/** Compose translated material and shape names without baking in the server's language. */
final class PieceNames {
    private static final Map<String, Set<String>> TRANSLATION_KEYS = new ConcurrentHashMap<>();

    private PieceNames() {
    }

    static Component displayName(String namespace, String material, String shape, Component nativeName) {
        String materialKey = "material." + namespace + "." + material.replace(':', '.');
        Component materialName = hasTranslation(namespace, materialKey) ? new TranslatableComponent(materialKey)
                : nativeName;
        String shapeKey = "pieces." + namespace + "." + shape;

        if (!hasTranslation(namespace, shapeKey)) {
            shapeKey = "pieces.skysbuildingpieces." + shape;
        }

        return new TranslatableComponent(shapeKey, materialName);
    }

    private static boolean hasTranslation(String namespace, String key) {
        return TRANSLATION_KEYS.computeIfAbsent(namespace, PieceNames::readKeys).contains(key);
    }

    private static Set<String> readKeys(String namespace) {
        InputStream input = PieceNames.class.getResourceAsStream("/assets/" + namespace + "/lang/en_us.json");

        // Java modules keep another mod's assets outside this class's resource
        // lookup. Read an add-on's own file without changing its language keys.
        if (input == null) {
            net.minecraftforge.forgespi.language.IModFileInfo owner =
                    net.minecraftforge.fml.ModList.get().getModFileById(namespace);
            if (owner != null) {
                java.nio.file.Path language = owner.getFile().findResource("assets", namespace, "lang", "en_us.json");
                if (java.nio.file.Files.isRegularFile(language)) {
                    try {
                        input = java.nio.file.Files.newInputStream(language);
                    } catch (java.io.IOException failure) {
                        throw new IllegalStateException("Cannot open the piece names for " + namespace, failure);
                    }
                }
            }
        }
        if (input == null) {
            return Collections.emptySet();
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject language = new JsonParser().parse(reader).getAsJsonObject();
            Set<String> keys = new HashSet<>();

            language.entrySet().forEach(entry -> keys.add(entry.getKey()));
            return Collections.unmodifiableSet(keys);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read the piece names for " + namespace, failure);
        }
    }

    static String format(String namespace, String material, String shape, String nativeName,
            Function<String, String> translate) {
        String materialKey = "material." + namespace + "." + material.replace(':', '.');
        String name = translate.apply(materialKey);

        if (name.equals(materialKey)) {
            name = nativeName;
        }

        String shapeKey = "pieces." + namespace + "." + shape;
        String pattern = translate.apply(shapeKey);

        if (pattern.equals(shapeKey)) {
            pattern = translate.apply("pieces.skysbuildingpieces." + shape);
        }
        try {
            return String.format(Locale.ROOT, pattern, name);
        } catch (IllegalFormatException malformed) {
            return "Format error: " + pattern;
        }
    }
}
