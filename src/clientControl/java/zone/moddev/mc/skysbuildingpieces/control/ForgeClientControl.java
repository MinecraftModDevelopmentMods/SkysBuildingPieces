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
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());

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
            net.minecraft.core.RegistryAccess.RegistryHolder registries =
                    net.minecraft.core.RegistryAccess.builtin();
            net.minecraft.core.Registry<net.minecraft.world.level.dimension.DimensionType> dimensions =
                    registries.registryOrThrow(net.minecraft.core.Registry.DIMENSION_TYPE_REGISTRY);
            net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes =
                    registries.registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY);
            net.minecraft.world.level.levelgen.WorldGenSettings defaults =
                    net.minecraft.world.level.levelgen.WorldGenSettings.makeDefault(dimensions, biomes,
                            registries.registryOrThrow(net.minecraft.core.Registry.NOISE_GENERATOR_SETTINGS_REGISTRY));
            net.minecraft.world.level.levelgen.WorldGenSettings generator =
                    new net.minecraft.world.level.levelgen.WorldGenSettings(4815162342L, false, false,
                            net.minecraft.world.level.levelgen.WorldGenSettings.withOverworld(dimensions,
                                    defaults.dimensions(), new net.minecraft.world.level.levelgen.FlatLevelSource(
                                            net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.getDefault(biomes))));
            net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                    "Forge Login Control", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.level.GameRules(),
                    net.minecraft.world.level.DataPackConfig.DEFAULT);
            client.createLevel("control-world", settings, registries, generator);
        } else if (client.player != null && client.level != null && ++joinedTicks >= 40) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("skysbuildingpieces")
                    || net.minecraftforge.fml.ModList.get().isLoaded("skysgrassslabs")) {
                throw new AssertionError("The Forge-only control contains a Sky mod");
            }

            org.apache.logging.log4j.LogManager.getLogger().info("FORGE37_CLIENT_CONTROL_PASS joined=true sky_mods=false");
            client.stop();
        }
    }
    private static void confirmDisposableWorld(Minecraft client) {
        net.minecraft.client.gui.screens.Screen screen = client.screen;
        if (screen == null || !(screen.getTitle() instanceof net.minecraft.network.chat.TranslatableComponent)
                || !((net.minecraft.network.chat.TranslatableComponent) screen.getTitle()).getKey()
                        .equals("selectWorld.backupQuestion.experimental")) {
            return;
        }
        net.minecraft.network.chat.Component proceed;
        if (screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            proceed = new net.minecraft.network.chat.TranslatableComponent("selectWorld.backupJoinSkipButton");
        } else if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
            proceed = net.minecraft.network.chat.CommonComponents.GUI_PROCEED;
        } else {
            return;
        }
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button) {
                net.minecraft.client.gui.components.Button button =
                        (net.minecraft.client.gui.components.Button) child;
                if (button.getMessage().equals(proceed)) {
                    // Only the marked probe's disposable world reaches this confirmation.
                    button.onPress();
                    return;
                }
            }
        }
        throw new AssertionError("The native experimental-world confirmation has no proceed action");
    }


}
