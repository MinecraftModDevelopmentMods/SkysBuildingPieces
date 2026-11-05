package zone.moddev.mc.skysbuildingpieces.control;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Diagnose native NeoForge startup without loading any content mod. */
@Mod("neoforgeservercontrol")
public final class NeoForgeServerControl {
    private net.minecraft.server.MinecraftServer server;
    private int ticks;

    public NeoForgeServerControl() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void started(ServerStartedEvent event) {
        ModList.get().getMods().forEach(mod -> {
            String identity = mod.getModId();

            if (!identity.equals("minecraft") && !identity.equals("neoforge") && !identity.equals("neoforgeservercontrol")) {
                throw new AssertionError("Unexpected mod in the NeoForge-only control: " + identity);
            }
        });

        server = event.getServer();
    }

    private void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // Keep the native watcher alive after startup so a delayed self-save notification can be observed.
        if (server != null && ++ticks == 200) {
            org.apache.logging.log4j.LogManager.getLogger().info("NEOFORGE_SERVER_CONTROL_PASS started=true sky_mods=false ticks=200");
            server.halt(false);
        }
    }
}
