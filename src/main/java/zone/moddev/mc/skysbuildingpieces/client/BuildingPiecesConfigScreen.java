package zone.moddev.mc.skysbuildingpieces.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import zone.moddev.mc.skysbuildingpieces.config.BuildingPiecesConfig;

/** Changes on this screen take effect after restarting Minecraft. */
public final class BuildingPiecesConfigScreen extends GuiConfig {
    public BuildingPiecesConfigScreen(GuiScreen parent) {
        super(parent, elements(), "skysbuildingpieces", false, true, "Sky's Building Pieces");
    }

    private static List<IConfigElement> elements() {
        List<IConfigElement> elements = new ArrayList<>();
        elements.addAll(new ConfigElement(BuildingPiecesConfig.configuration().getCategory("gameplay")).getChildElements());
        elements.addAll(new ConfigElement(BuildingPiecesConfig.configuration().getCategory("compat")).getChildElements());
        return elements;
    }
}
