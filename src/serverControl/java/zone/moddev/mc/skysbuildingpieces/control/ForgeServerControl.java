package zone.moddev.mc.skysbuildingpieces.control;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmlserverevents.FMLServerStartedEvent;

/** Diagnose native Forge startup without loading any content mod. */
@Mod("forgeservercontrol")
public final class ForgeServerControl {
    private net.minecraft.server.MinecraftServer server;
    private int ticks;

    public ForgeServerControl() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void started(FMLServerStartedEvent event) {
        ModList.get().getMods().forEach(mod -> {
            String identity = mod.getModId();

            if (!identity.equals("minecraft") && !identity.equals("forge") && !identity.equals("forgeservercontrol")) {
                throw new AssertionError("Unexpected mod in the Forge-only control: " + identity);
            }
        });

        server = event.getServer();
    }

    private void tick(TickEvent.ServerTickEvent event) {
        // Keep the native watcher alive after startup so a delayed self-save notification can be observed.
        if (server != null && event.phase == TickEvent.Phase.END && ++ticks == 200) {
            org.apache.logging.log4j.LogManager.getLogger().info("FORGE37_SERVER_CONTROL_PASS started=true sky_mods=false ticks=200");
            server.halt(false);
        }
    }
}
