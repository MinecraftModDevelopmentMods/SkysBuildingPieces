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
                    .newInstance("40.3.0", "1.18.2", "20220404.173914", "net.minecraftforge");
            set(loader, "versionInfo", forgeVersion);
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            initialized = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize the Forge 40 test runtime",
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
