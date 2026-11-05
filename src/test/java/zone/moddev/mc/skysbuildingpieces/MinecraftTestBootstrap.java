package zone.moddev.mc.skysbuildingpieces;

import cpw.mods.modlauncher.api.IModuleLayerManager;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;

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
            var versions = FMLLoader.versionInfo();
            if (!versions.forgeVersion().equals("61.1.0") || !versions.mcVersion().equals("1.21.11")
                    || !versions.mcpVersion().equals("20251223.124241")) {
                throw new IllegalStateException("Wrong Forge or Minecraft version for the focused tests");
            }
            SharedConstants.tryDetectVersion();
            // Plain JUnit has no ModLauncher event transformation. Build the
            // registries here; real Forge launches cover the networking bootstrap.
            if (FMLLoader.getLoadingModList() == null) {
                LoadingModList vanillaOnly = LoadingModList.of(List.of(), List.of(), null);
                vanillaOnly.setBrokenFiles(List.of());
                set(FMLLoader.class, "loadingModList", vanillaOnly);
                IModuleLayerManager layers = layer -> layer == IModuleLayerManager.Layer.GAME
                        ? Optional.of(ModuleLayer.boot()) : Optional.empty();
                set(FMLLoader.class, "moduleLayerManager", layers);
            }
            set(Bootstrap.class, "isBootstrapped", true);
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.size();
            net.minecraft.core.registries.BuiltInRegistries.bootStrap();
            net.minecraftforge.registries.GameData.vanillaSnapshot();
            // Focused tests create blocks outside Forge's normal registration event.
            ((net.minecraft.core.MappedRegistry<?>)
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK).unfreeze();
            ((net.minecraft.core.MappedRegistry<?>)
                    net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
            ((net.minecraftforge.registries.ForgeRegistry<?>)
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS).unfreeze();
            ((net.minecraftforge.registries.ForgeRegistry<?>)
                    net.minecraftforge.registries.ForgeRegistries.ITEMS).unfreeze();
            initialized = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize the Forge 61 test runtime",
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
