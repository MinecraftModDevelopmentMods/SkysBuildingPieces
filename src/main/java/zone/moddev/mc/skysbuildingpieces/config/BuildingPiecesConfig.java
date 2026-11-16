package zone.moddev.mc.skysbuildingpieces.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class BuildingPiecesConfig {
    private static final String GAMEPLAY = "gameplay";
    private static final String COMPAT = "compat";
    private static final String DIRECTIONAL_SLABS = "directionalSlabPlacement";
    private static final String REPLACE_PIECES = "forceReplaceBuildingBricksPieces";

    private static Configuration configuration;
    private static boolean directionalSlabs = true;

    private BuildingPiecesConfig() {
    }

    public static boolean load(File file) {
        configuration = new Configuration(file);
        configuration.load();
        directionalSlabs = configuration.getBoolean(DIRECTIONAL_SLABS, GAMEPLAY, true,
                "Use click position to place regular slabs horizontally or vertically. Requires a restart.");
        boolean replacePieces = configuration.getBoolean(REPLACE_PIECES, COMPAT, false,
                "Replace supported BuildingBricks pieces as they load. Back up the world first. Requires restart.");

        configuration.setCategoryRequiresMcRestart(GAMEPLAY, true);
        configuration.setCategoryRequiresMcRestart(COMPAT, true);
        configuration.getCategory(GAMEPLAY).get(DIRECTIONAL_SLABS)
                .setLanguageKey("config.skysbuildingpieces.directionalSlabPlacement");
        configuration.getCategory(COMPAT).get(REPLACE_PIECES)
                .setLanguageKey("config.skysbuildingpieces.forceReplaceBuildingBricksPieces");
        saveChanges();
        return replacePieces;
    }

    public static boolean directionalSlabPlacement() {
        return directionalSlabs;
    }

    public static Configuration configuration() {
        return configuration;
    }

    public static void saveChanges() {
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
