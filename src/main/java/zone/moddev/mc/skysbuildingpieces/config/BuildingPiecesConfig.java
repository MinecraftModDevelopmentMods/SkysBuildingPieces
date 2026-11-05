package zone.moddev.mc.skysbuildingpieces.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

/** Recovery choices are read at startup; old Forge CFG files are kept intact. */
public final class BuildingPiecesConfig {
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue FORCE_REPLACEMENT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("compat");
        FORCE_REPLACEMENT = builder
                .comment("Replace supported historical pieces when their original mod is installed.",
                        "Back up the world before enabling this. Requires restart.",
                        "Recovery is automatic when the original mod is absent.")
                .worldRestart().define("forceReplaceBuildingBricksPieces", false);
        builder.pop();
        SPEC = builder.build();
    }

    private BuildingPiecesConfig() {
    }

    public static void register(ModContainer container) {
        migrateLegacyConfig();
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    public static boolean forceReplacement() {
        return FORCE_REPLACEMENT.get();
    }

    private static void migrateLegacyConfig() {
        Path directory = FMLPaths.CONFIGDIR.get();
        Path current = directory.resolve(SkysBuildingPieces.ID + "-common.toml");
        Path legacy = directory.resolve(SkysBuildingPieces.ID + ".cfg");

        if (Files.exists(current) || !Files.isRegularFile(legacy)) {
            return;
        }
        try {
            String contents = new String(Files.readAllBytes(legacy), StandardCharsets.UTF_8);
            Matcher setting = Pattern.compile("(?m)^\\s*B:forceReplaceBuildingBricksPieces\\s*=\\s*(true|false)\\s*$")
                    .matcher(contents);
            boolean enabled = setting.find() && Boolean.parseBoolean(setting.group(1));

            Files.createDirectories(directory);
            Files.write(current,
                    ("[compat]\nforceReplaceBuildingBricksPieces=" + enabled + "\n").getBytes(StandardCharsets.UTF_8));
            SkysBuildingPieces.LOGGER.info("Copied the existing Building Pieces recovery setting to TOML; "
                    + "the original CFG file is unchanged.");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot preserve the Building Pieces recovery setting", failure);
        }
    }
}
