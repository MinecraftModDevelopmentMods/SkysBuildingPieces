package zone.moddev.mc.skysbuildingpieces.control;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** A Forge-only login control, compiled and launched without the Sky mods. */
@Mod("forgeclientcontrol")
public final class ForgeClientControl {
    private boolean started;
    private int joinedTicks;
    private long worldStarted;

    public ForgeClientControl() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent.Post event) {

        Minecraft client = Minecraft.getInstance();

        org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle());

        if (started && client.level == null) {
            confirmDisposableWorld(client);
            if (System.nanoTime() - worldStarted > java.util.concurrent.TimeUnit.SECONDS.toNanos(180)) {
                org.apache.logging.log4j.LogManager.getLogger().error("Disposable Forge control world did not start",
                        new AssertionError("Native client startup timed out"));
                client.stop();
                return;
            }
        }
        if (!started) {
            started = true;
            worldStarted = System.nanoTime();
            LevelSettings settings = new LevelSettings("Forge Login Control", GameType.CREATIVE,
                            net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                    net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("control-world", settings,
                    new net.minecraft.world.level.levelgen.WorldOptions(4815162342L, false, false),
                    net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, client.screen);
        } else if (client.player != null && client.level != null && ++joinedTicks >= 40) {
            if (net.minecraftforge.fml.ModList.isLoaded("skysbuildingpieces")
                    || net.minecraftforge.fml.ModList.isLoaded("skysgrassslabs")) {
                throw new AssertionError("The Forge-only control contains a Sky mod");
            }

            org.apache.logging.log4j.LogManager.getLogger().info("FORGE61_CLIENT_CONTROL_PASS joined=true sky_mods=false");
            client.stop();
        }
    }
    private static void confirmDisposableWorld(Minecraft minecraft) {
        boolean backup = minecraft.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen;
        boolean confirmation = minecraft.screen != null
                && minecraft.screen.getClass() == net.minecraft.client.gui.screens.ConfirmScreen.class;
        if (!backup && !confirmation) {
            return;
        }
        java.nio.file.Path save = minecraft.gameDirectory.toPath().resolve("saves")
                .resolve("control-world").resolve("level.dat");
        if (!java.nio.file.Files.isRegularFile(save)) {
            throw new IllegalStateException("The load prompt has no saved probe world");
        }
        String experimentalTitle = net.minecraft.network.chat.Component
                .translatable("selectWorld.backupQuestion.experimental").getString();
        if (confirmation && !experimentalTitle.equals(minecraft.screen.getTitle().getString())) {
            throw new IllegalStateException("Unexpected world-load confirmation: "
                    + minecraft.screen.getTitle().getString());
        }
        String proceed = backup
                ? net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinConfirmButton").getString()
                : net.minecraft.network.chat.CommonComponents.GUI_PROCEED.getString();
        for (var child : minecraft.screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button
                    && proceed.equals(button.getMessage().getString())) {
                if (button.active) {
                    // Confirm the known load prompt for this generated fixture.
                    button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                    System.out.println("FORGE47_CLIENT_CONTROL_PROMPT_CONFIRMED owned_world=true");
                }
                return;
            }
        }
        throw new IllegalStateException("The expected world-load confirmation button is missing");
    }


}
