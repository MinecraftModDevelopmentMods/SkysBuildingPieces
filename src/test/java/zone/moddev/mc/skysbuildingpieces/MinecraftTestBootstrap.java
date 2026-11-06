package zone.moddev.mc.skysbuildingpieces;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.GameData;

public final class MinecraftTestBootstrap {
    private static boolean initialized;

    public static net.minecraft.world.level.LevelHeightAccessor overworldHeight() {
        return net.minecraft.world.level.LevelHeightAccessor.create(0, 256);
    }

    public static net.minecraft.nbt.CompoundTag overworldUpgradeContext() {
        var context = new net.minecraft.nbt.CompoundTag();
        context.putString("dimension", "minecraft:overworld");
        context.putString("generator", "minecraft:noise");
        return context;
    }

    public static synchronized void registerVanilla() {
        if (initialized) return;
        try {
            // Give vanilla's registry setup an empty mod list in the unit-test JVM.
            // The native runtime tests exercise the real loader and event buses.
            FMLLoader loader = FMLLoader.getCurrentOrNull();
            if (loader == null) {
                var constructor = FMLLoader.class.getDeclaredConstructor(
                        ClassLoader.class, String[].class, Dist.class, boolean.class, Path.class);
                constructor.setAccessible(true);
                loader = constructor.newInstance(ClassLoader.getSystemClassLoader(),
                        new String[0], Dist.CLIENT, false, Path.of(".").toAbsolutePath().normalize());
            }
            Field loadingMods = FMLLoader.class.getDeclaredField("loadingModList");
            loadingMods.setAccessible(true);
            loadingMods.set(loader, LoadingModList.of(List.of(), List.of(), List.of(),
                    List.of(), List.of(), Map.of()));
            SharedConstants.tryDetectVersion();
            if (!SharedConstants.getCurrentVersion().name().equals("26.3")) {
                throw new IllegalStateException("Wrong Minecraft version for the focused tests");
            }
            set(Bootstrap.class, "isBootstrapped", true);
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.size();
            net.minecraft.core.registries.BuiltInRegistries.bootStrap();
            GameData.vanillaSnapshot();
            // The test fixtures create blocks outside NeoForge's registration events.
            ((net.minecraft.core.MappedRegistry<?>)
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK).unfreeze(false);
            ((net.minecraft.core.MappedRegistry<?>)
                    net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze(false);
            initialized = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize the NeoForge 26.3 test runtime",
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
