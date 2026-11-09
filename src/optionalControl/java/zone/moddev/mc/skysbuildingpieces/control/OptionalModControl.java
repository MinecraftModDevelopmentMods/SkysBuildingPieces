package zone.moddev.mc.skysbuildingpieces.control;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;

/** A dependency-only startup control, launched without any Sky content. */
@Mod("optionalmodcontrol")
public final class OptionalModControl {
    public OptionalModControl() {
        ServerStartedEvent.BUS.addListener(this::started);
    }

    private void started(ServerStartedEvent event) {
        if (!ModList.isLoaded("mineralogy") || !ModList.isLoaded("orespawn")
                || ModList.isLoaded("skysbuildingpieces") || ModList.isLoaded("skysgrassslabs")) {
            throw new AssertionError("Dependency control contains the wrong mods");
        }

        org.apache.logging.log4j.LogManager.getLogger().info("OPTIONAL_MOD_CONTROL_STARTED sky_mods=false");
        event.getServer().halt(false);
    }
}
