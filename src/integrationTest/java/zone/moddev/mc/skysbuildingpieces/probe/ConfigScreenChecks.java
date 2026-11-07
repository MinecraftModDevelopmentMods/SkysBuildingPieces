package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.apache.logging.log4j.LogManager;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

final class ConfigScreenChecks {
    private ConfigScreenChecks() {
    }

    static void run(Minecraft minecraft) throws IOException {
        Screen parent = minecraft.screen;
        var mod = ModList.get().getModContainerById(SkysBuildingPieces.ID).orElseThrow();
        var factory = IConfigScreenFactory.getForMod(mod.getModInfo()).orElseThrow();
        Screen config = factory.createScreen(mod, parent);
        if (!(config instanceof ConfigurationScreen)) {
            throw new AssertionError("Building Pieces did not register the standard NeoForge config screen");
        }

        minecraft.setScreen(config);
        changeSlabPlacement(minecraft, true);
        minecraft.setScreen(factory.createScreen(mod, parent));
        changeSlabPlacement(minecraft, false);
        minecraft.setScreen(parent);

        LogManager.getLogger().info("BUILDING_PIECES_CONFIG_SCREEN_PASS default_on=true saves_off=true restores_on=true");
    }

    private static void changeSlabPlacement(Minecraft minecraft, boolean enabled) throws IOException {
        Screen common = minecraft.screen;
        String gameplay = I18n.get("skysbuildingpieces.configuration.gameplay.button");
        Button category = controls(common).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().contains(gameplay)).findFirst().orElseThrow();
        category.onPress();
        Screen section = minecraft.screen;
        CycleButton<?> toggle = controls(section).filter(CycleButton.class::isInstance).map(CycleButton.class::cast)
                .findFirst().orElseThrow();
        if (!Boolean.valueOf(enabled).equals(toggle.getValue())) {
            throw new AssertionError("Unexpected initial slab placement toggle value: " + toggle.getValue());
        }

        toggle.onPress();
        section.onClose();
        common.onClose();
        var file = FMLPaths.CONFIGDIR.get().resolve(SkysBuildingPieces.ID + "-common.toml");
        var saved = Pattern.compile("(?m)^\\s*directionalSlabPlacement\\s*=\\s*" + !enabled + "\\s*$");
        if (!saved.matcher(Files.readString(file)).find()) {
            throw new AssertionError("Slab placement toggle did not save " + !enabled);
        }
    }

    private static Stream<GuiEventListener> controls(GuiEventListener widget) {
        if (widget instanceof ContainerEventHandler container) {
            return container.children().stream().flatMap(ConfigScreenChecks::controls);
        }
        return Stream.of(widget);
    }
}
