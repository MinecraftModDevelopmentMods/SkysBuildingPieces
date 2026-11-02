package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fmllegacy.WorldPersistenceHooks;
import org.apache.logging.log4j.LogManager;

/** Checks the real loading stages around Forge's shared persistence-hook list. */
public final class PersistenceRegistrationChecks {
    private static final String CORE = "zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge$";
    private static final String GRASS = "zone.moddev.mc.skysgrassslabs.compat.LegacyWorldDataHook$";
    private static final String MINERALOGY = "zone.moddev.mc.mineralogy.patching.LegacyWorldDataHook$";

    private PersistenceRegistrationChecks() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(PersistenceRegistrationChecks::beforeQueuedWork);
        bus.addListener(PersistenceRegistrationChecks::afterQueuedWork);
    }

    private static void beforeQueuedWork(FMLCommonSetupEvent event) {
        require(count(CORE) == 0 && count(GRASS) == 0,
                "Sky persistence hooks must wait until parallel setup has finished");
        LogManager.getLogger().info("SKY_PERSISTENCE_SETUP_BEFORE_PASS sky_hooks=0");
    }

    private static void afterQueuedWork(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            require(!Thread.currentThread().getName().startsWith("modloading-worker"),
                    "Queued verification must run outside parallel mod loading");
            require(count(CORE) == (ModList.get().isLoaded("skysbuildingpieces") ? 1 : 0),
                    "Building Pieces must register exactly one persistence hook");
            require(count(GRASS) == (ModList.get().isLoaded("skysgrassslabs") ? 1 : 0),
                    "Grass Slabs must register exactly one persistence hook");
            require(count(MINERALOGY) == (ModList.get().isLoaded("mineralogy") ? 1 : 0),
                    "Mineralogy's independent persistence hook must remain installed");
            LogManager.getLogger().info("SKY_PERSISTENCE_SETUP_AFTER_PASS core={} grass={} mineralogy={}",
                    count(CORE), count(GRASS), count(MINERALOGY));
        });
    }

    private static long count(String prefix) {
        try {
            Field field = WorldPersistenceHooks.class.getDeclaredField("worldPersistenceHooks");
            field.setAccessible(true);
            return ((List<?>) field.get(null)).stream()
                    .filter(hook -> hook.getClass().getName().startsWith(prefix)).count();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect the native persistence callbacks", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
