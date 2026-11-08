package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.config.BuildingPiecesConfig;

final class ConfigScreenChecks {
    private ConfigScreenChecks() {
    }

    static void run(Minecraft minecraft) throws IOException {
        Screen parent = minecraft.gui.screen();
        var mod = ModList.getModContainerById(SkysBuildingPieces.ID).orElseThrow();
        var factory = ConfigScreenHandler.getScreenFactoryFor(mod.getModInfo()).orElseThrow();
        minecraft.gui.setScreen(factory.apply(minecraft, parent));
        toggleSlabPlacement(minecraft.gui.screen(), true);
        if (!BuildingPiecesConfig.directionalSlabPlacement()) {
            throw new AssertionError("A pending setting changed the running game");
        }

        minecraft.gui.setScreen(factory.apply(minecraft, parent));
        toggleSlabPlacement(minecraft.gui.screen(), false);
        minecraft.gui.setScreen(parent);

        LogManager.getLogger().info("BUILDING_PIECES_CONFIG_SCREEN_PASS default_on=true saves_off=true restores_on=true");
    }

    private static void toggleSlabPlacement(Screen screen, boolean enabled) throws IOException {
        String name = I18n.get("skysbuildingpieces.configuration.directionalSlabPlacement");
        CycleButton<?> toggle = screen.children().stream()
                .filter(CycleButton.class::isInstance).map(CycleButton.class::cast)
                .filter(button -> button.getMessage().getString().contains(name))
                .findFirst().orElseThrow();
        if (!Boolean.valueOf(enabled).equals(toggle.getValue())) {
            throw new AssertionError("Unexpected initial slab placement toggle value: " + toggle.getValue());
        }
        KeyEvent enter = new KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0);
        toggle.onPress(enter);
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(I18n.get("gui.done")))
                .findFirst().orElseThrow().onPress(enter);

        var file = FMLPaths.CONFIGDIR.get().resolve(SkysBuildingPieces.ID + "-common.toml");
        var saved = Pattern.compile("(?m)^\\s*directionalSlabPlacement\\s*=\\s*" + !enabled + "\\s*$");
        if (!saved.matcher(Files.readString(file)).find()) {
            throw new AssertionError("Slab placement toggle did not save " + !enabled);
        }
    }
}
