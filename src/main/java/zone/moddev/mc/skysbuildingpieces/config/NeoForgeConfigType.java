package zone.moddev.mc.skysbuildingpieces.config;

import net.neoforged.fml.config.ModConfig;

/** Keeps the same local configuration scope across the NeoForge 26.2 rename. */
final class NeoForgeConfigType {
    private NeoForgeConfigType() {
    }

    static ModConfig.Type local() {
        // Resolve names at runtime so one jar supports both loader versions.
        try {
            return ModConfig.Type.valueOf("LOCAL");
        } catch (IllegalArgumentException missingLocalName) {
            return ModConfig.Type.valueOf("COMMON");
        }
    }
}
