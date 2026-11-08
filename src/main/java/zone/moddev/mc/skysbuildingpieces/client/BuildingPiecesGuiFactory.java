package zone.moddev.mc.skysbuildingpieces.client;

import java.util.Collections;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.config.BuildingPiecesConfig;

/** Connects the Mods list's Config button to Forge's standard settings screen. */
public final class BuildingPiecesGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new BuildingPiecesConfigScreen(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @SubscribeEvent
    public void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if ("skysbuildingpieces".equals(event.getModID())) {
            // Keep the active settings until Minecraft restarts.
            BuildingPiecesConfig.saveChanges();
        }
    }
}
