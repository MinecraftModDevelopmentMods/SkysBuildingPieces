package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.config.BuildingPiecesConfig;

final class ConfigScreenChecks {
    private ConfigScreenChecks() {
    }

    static void run(Minecraft minecraft) throws ReflectiveOperationException {
        ModContainer mod = Loader.instance().getIndexedModList().get(SkysBuildingPieces.ID);
        IModGuiFactory factory = FMLClientHandler.instance().getGuiFactoryFor(mod);
        GuiConfig screen = (GuiConfig) factory.mainConfigGuiClass().getConstructor(GuiScreen.class).newInstance(minecraft.currentScreen);
        if (!screen.allRequireMcRestart || screen.configElements.size() != 2) {
            throw new AssertionError("Building Pieces should have two restart-required settings");
        }

        for (IConfigElement entry : screen.configElements) {
            String key = entry.getLanguageKey();
            if (I18n.format(key).equals(key) || I18n.format(key + ".tooltip").equals(key + ".tooltip")) {
                throw new AssertionError("Missing Building Pieces setting label or tooltip " + key);
            }
        }

        Configuration configuration = BuildingPiecesConfig.configuration();
        Property placement = configuration.getCategory("gameplay").get("directionalSlabPlacement");
        if (!placement.getBoolean() || !BuildingPiecesConfig.directionalSlabPlacement()) {
            throw new AssertionError("Directional slab placement must default to on");
        }
        try {
            placement.set(false);
            MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent.OnConfigChangedEvent(
                    SkysBuildingPieces.ID, null, false, true));
            Configuration saved = new Configuration(configuration.getConfigFile());
            saved.load();
            if (saved.get("gameplay", "directionalSlabPlacement", true).getBoolean()
                    || !BuildingPiecesConfig.directionalSlabPlacement()) {
                throw new AssertionError("The placement option should save for the next restart");
            }
        } finally {
            placement.set(true);
            BuildingPiecesConfig.saveChanges();
        }
        System.out.println("BUILDING_PIECES_CONFIG_SCREEN_PASS default_on=true saves_off=true restores_on=true");
    }
}
