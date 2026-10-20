package zone.moddev.mc.skysbuildingpieces.content;

import java.util.Locale;
import java.util.IllegalFormatException;
import java.util.function.Function;
import net.minecraft.util.text.translation.I18n;

/** Piece labels use material names, not the names of their full-block ingredients. */
final class PieceNames {
    private PieceNames() {
    }

    static String displayName(String namespace, String material, String shape, String nativeName) {
        return format(namespace, material, shape, nativeName, I18n::translateToLocal);
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
