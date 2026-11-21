package zone.moddev.mc.skysbuildingpieces.content;

import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

/** Flat inventory identities; the placed blocks retain their permanent palettes. */
final class PaletteItemIds {
    private PaletteItemIds() {
    }

    static String itemId(Catalogue.Palette palette, String material) {
        if (!palette.materials.contains(material)) {
            throw new IllegalArgumentException("Material is not in " + palette.registryId() + ": " + material);
        }

        String materialPath = material.substring(material.indexOf(':') + 1);
        String prefix = palette.module.equals("mineralogy") ? "mineralogy_" : "";

        return palette.namespace + ":" + prefix + materialPath + "_"
                + palette.shape.name().toLowerCase(java.util.Locale.ROOT);
    }

    static String fromLegacyMetadata(Catalogue.Palette palette, int metadata) {
        if (metadata < 0 || metadata > 15) {
            throw new IllegalArgumentException("Invalid saved metadata for " + palette.registryId());
        }

        // Orientation belongs to the placed state. Inventory items retain the material and acquire their orientation
        // from the next placement click.
        return itemId(palette, palette.material(metadata));
    }
}
