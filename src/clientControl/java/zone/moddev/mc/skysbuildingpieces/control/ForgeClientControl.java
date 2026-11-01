package zone.moddev.mc.skysbuildingpieces.control;

import net.minecraft.client.Minecraft;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
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
                new AssertionError("Disposable Forge control world did not start").printStackTrace();
                client.stop();
                return;
            }
        }
        if (!started) {
            started = true;
            worldStarted = System.nanoTime();
            net.minecraft.util.registry.DynamicRegistries.Impl registries =
                    net.minecraft.util.registry.DynamicRegistries.builtin();
            net.minecraft.util.registry.Registry<net.minecraft.world.DimensionType> dimensions =
                    registries.registryOrThrow(net.minecraft.util.registry.Registry.DIMENSION_TYPE_REGISTRY);
            net.minecraft.util.registry.Registry<net.minecraft.world.biome.Biome> biomes =
                    registries.registryOrThrow(net.minecraft.util.registry.Registry.BIOME_REGISTRY);
            net.minecraft.world.gen.settings.DimensionGeneratorSettings defaults =
                    net.minecraft.world.gen.settings.DimensionGeneratorSettings.makeDefault(dimensions, biomes,
                            registries.registryOrThrow(net.minecraft.util.registry.Registry.NOISE_GENERATOR_SETTINGS_REGISTRY));
            net.minecraft.world.gen.settings.DimensionGeneratorSettings generator =
                    new net.minecraft.world.gen.settings.DimensionGeneratorSettings(4815162342L, false, false,
                            net.minecraft.world.gen.settings.DimensionGeneratorSettings.withOverworld(dimensions,
                                    defaults.dimensions(), new net.minecraft.world.gen.FlatChunkGenerator(
                                            net.minecraft.world.gen.FlatGenerationSettings.getDefault(biomes))));
            net.minecraft.world.WorldSettings settings = new net.minecraft.world.WorldSettings(
                    "Forge Login Control", net.minecraft.world.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(),
                    net.minecraft.util.datafix.codec.DatapackCodec.DEFAULT);
            client.createLevel("control-world", settings, registries, generator);
        } else if (client.player != null && client.level != null && ++joinedTicks >= 40) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("skysbuildingpieces")
                    || net.minecraftforge.fml.ModList.get().isLoaded("skysgrassslabs")) {
                throw new AssertionError("The Forge-only control contains a Sky mod");
            }

            System.out.println("FORGE36_CLIENT_CONTROL_PASS joined=true sky_mods=false");
            client.stop();
        }
    }
    private static void confirmDisposableWorld(Minecraft client) {
        net.minecraft.client.gui.screen.Screen screen = client.screen;
        if (screen == null || !(screen.getTitle() instanceof net.minecraft.util.text.TranslationTextComponent)
                || !((net.minecraft.util.text.TranslationTextComponent) screen.getTitle()).getKey()
                        .equals("selectWorld.backupQuestion.experimental")) {
            return;
        }
        net.minecraft.util.text.ITextComponent proceed;
        if (screen instanceof net.minecraft.client.gui.screen.ConfirmBackupScreen) {
            proceed = new net.minecraft.util.text.TranslationTextComponent("selectWorld.backupJoinSkipButton");
        } else if (screen instanceof net.minecraft.client.gui.screen.ConfirmScreen) {
            proceed = net.minecraft.client.gui.DialogTexts.GUI_PROCEED;
        } else {
            return;
        }
        for (net.minecraft.client.gui.IGuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.widget.button.Button) {
                net.minecraft.client.gui.widget.button.Button button =
                        (net.minecraft.client.gui.widget.button.Button) child;
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
