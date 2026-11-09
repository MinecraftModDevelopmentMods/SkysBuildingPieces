package zone.moddev.mc.skysbuildingpieces.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

/** Building and recovery settings shared by the client and server. */
public final class BuildingPiecesConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue FORCE_REPLACEMENT;
    private static final ForgeConfigSpec.BooleanValue DIRECTIONAL_SLAB_PLACEMENT;
    private static boolean directionalSlabPlacement = true;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("compat");
        FORCE_REPLACEMENT = builder
                .comment("Replace supported historical pieces when their original mod is installed.",
                        "Back up the world before enabling this. Requires restart.",
                        "Recovery is automatic when the original mod is absent.")
                .worldRestart().define("forceReplaceBuildingBricksPieces", false);
        builder.pop();
        builder.push("gameplay");
        DIRECTIONAL_SLAB_PLACEMENT = builder
                .comment("Allow regular slabs to be placed vertically with face and edge clicks.",
                        "Use the same setting on the client and server. Requires a restart.")
                .worldRestart().define("directionalSlabPlacement", true);
        builder.pop();
        SPEC = builder.build();
    }

    private BuildingPiecesConfig() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        migrateLegacyConfig();
        context.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    public static boolean forceReplacement() {
        return FORCE_REPLACEMENT.get();
    }

    public static void activate() {
        directionalSlabPlacement = configuredSlabPlacement();
    }

    public static boolean directionalSlabPlacement() {
        return directionalSlabPlacement;
    }

    public static boolean configuredSlabPlacement() {
        return !SPEC.isLoaded() || DIRECTIONAL_SLAB_PLACEMENT.get();
    }

    public static void saveForNextStart(boolean directionalSlabs, boolean replaceLegacy) {
        DIRECTIONAL_SLAB_PLACEMENT.set(directionalSlabs);
        FORCE_REPLACEMENT.set(replaceLegacy);
        SPEC.save();
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
