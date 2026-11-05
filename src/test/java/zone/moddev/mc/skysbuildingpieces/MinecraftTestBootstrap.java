package zone.moddev.mc.skysbuildingpieces;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

public final class MinecraftTestBootstrap {
    private static boolean initialized;

    public static net.minecraft.world.level.LevelHeightAccessor overworldHeight() {
        return new net.minecraft.world.level.LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return 256;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }
        };
    }

    public static synchronized void registerVanilla() {
        if (initialized) return;
        try {
            SharedConstants.tryDetectVersion();
            if (!SharedConstants.getCurrentVersion().getName().equals("1.21.1")) {
                throw new IllegalStateException("Wrong Minecraft version for the focused tests");
            }
            // NeoForge reads the discovered mods when Minecraft creates its feature flags.
            // These tests load vanilla registries without starting mod discovery.
            if (LoadingModList.get() == null) {
                LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
            }
            // Plain JUnit has no ModLauncher event transformation. Build the
            // registries here; real NeoForge launches cover the networking bootstrap.
            set(Bootstrap.class, "isBootstrapped", true);
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.size();
            net.minecraft.core.registries.BuiltInRegistries.bootStrap();
            // Focused tests create blocks outside NeoForge's normal registration event.
            ((net.minecraft.core.MappedRegistry<?>)
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK).unfreeze();
            ((net.minecraft.core.MappedRegistry<?>)
                    net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
            initialized = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize the NeoForge 21.1 test runtime",
                    exception);
        }
    }

    private static void set(Class<?> owner, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private MinecraftTestBootstrap() {
    }
}
