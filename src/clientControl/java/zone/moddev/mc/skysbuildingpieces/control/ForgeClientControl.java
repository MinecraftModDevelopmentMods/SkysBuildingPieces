package zone.moddev.mc.skysbuildingpieces.control;

import net.minecraft.client.Minecraft;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** A Forge-only login control, compiled and launched without the Sky mods. */
@Mod("forgeclientcontrol")
public final class ForgeClientControl {
    private boolean started;
    private int joinedTicks;

    public ForgeClientControl() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        org.lwjgl.glfw.GLFW.glfwHideWindow(client.getMainWindow().getHandle());

        if (!started) {
            started = true;
            client.launchIntegratedServer("control-world", "Forge Login Control",
                    new WorldSettings(4815162342L, GameType.CREATIVE, true, false, WorldType.FLAT));
        } else if (client.player != null && client.world != null && ++joinedTicks >= 40) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("skysbuildingpieces")
                    || net.minecraftforge.fml.ModList.get().isLoaded("skysgrassslabs")) {
                throw new AssertionError("The Forge-only control contains a Sky mod");
            }

            System.out.println("FORGE31_CLIENT_CONTROL_PASS joined=true sky_mods=false");
            client.shutdown();
        }
    }
}
