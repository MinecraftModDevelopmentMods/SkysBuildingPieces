package zone.moddev.mc.skysbuildingpieces.control;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** A dependency-only startup control, launched without any Sky content. */
@Mod("optionalmodcontrol")
public final class OptionalModControl {
    public OptionalModControl() {
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void started(ServerStartedEvent event) {
        if (!ModList.get().isLoaded("mineralogy") || !ModList.get().isLoaded("orespawn")
                || ModList.get().isLoaded("skysbuildingpieces") || ModList.get().isLoaded("skysgrassslabs")) {
            throw new AssertionError("Dependency control contains the wrong mods");
        }

        org.apache.logging.log4j.LogManager.getLogger().info("OPTIONAL_MOD_CONTROL_STARTED sky_mods=false");
        event.getServer().halt(false);
    }
}
