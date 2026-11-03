package zone.moddev.mc.skysbuildingpieces;

import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

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
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Class<?> versionInfo = Class.forName("net.minecraftforge.fml.loading.VersionInfo");
            Object forgeVersion = versionInfo
                    .getConstructor(String.class, String.class, String.class, String.class)
                    .newInstance("45.4.0", "1.19.4", "20230314.122934", "net.minecraftforge");
            set(loader, "versionInfo", forgeVersion);
            SharedConstants.tryDetectVersion();
            // Plain JUnit has no ModLauncher event transformation. Build the
            // registries here; real Forge launches cover the networking bootstrap.
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
            throw new IllegalStateException("Unable to initialize the Forge 45 test runtime",
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
