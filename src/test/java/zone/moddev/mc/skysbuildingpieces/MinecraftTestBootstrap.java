package zone.moddev.mc.skysbuildingpieces;

import java.lang.reflect.Field;
import net.minecraft.util.registry.Bootstrap;

public final class MinecraftTestBootstrap {
    private static boolean initialized;

    public static synchronized void registerVanilla() {
        if (initialized) {
            return;
        }
        try {
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");

            set(loader, "mcVersion", "1.16.5");
            set(loader, "mcpVersion", "20210115.111550");
            set(loader, "forgeVersion", "36.2.34");
            set(loader, "forgeGroup", "net.minecraftforge");
            Bootstrap.bootStrap();
            initialized = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize the Forge 36 test runtime", exception);
        }
    }

    private static void set(Class<?> owner, String name, String value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);

        field.setAccessible(true);
        field.set(null, value);
    }

    private MinecraftTestBootstrap() {
    }
}
