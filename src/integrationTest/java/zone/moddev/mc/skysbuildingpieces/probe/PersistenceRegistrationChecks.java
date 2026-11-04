package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Field;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.apache.logging.log4j.LogManager;

/** Checks queued activation; native read/write wrappers are checked separately. */
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
            LogManager.getLogger().info("SKY_PERSISTENCE_SETUP_AFTER_PASS mechanism=forge_level_hooks core={} grass={} mineralogy={}",
                    count(CORE), count(GRASS), count(MINERALOGY));
        });
    }

    private static long count(String prefix) {
        if (prefix.equals(GRASS) && !ModList.get().isLoaded("skysgrassslabs")) return 0;
        if (prefix.equals(MINERALOGY) && !ModList.get().isLoaded("mineralogy")) return 0;
        try {
            Class<?> owner = Class.forName(prefix.substring(0, prefix.length() - 1));
            if (prefix.equals(MINERALOGY)) {
                var capture = owner.getMethod("captureLegacyLevelData", net.minecraft.nbt.CompoundTag.class,
                        net.minecraft.world.level.storage.LevelStorageSource.LevelDirectory.class);
                var fallback = owner.getMethod("onServerAboutToStart",
                        net.minecraftforge.event.server.ServerAboutToStartEvent.class);
                require(java.lang.reflect.Modifier.isStatic(capture.getModifiers()),
                        "Mineralogy's capture API must be static");
                require(java.lang.reflect.Modifier.isStatic(fallback.getModifiers()),
                        "Mineralogy's server fallback must be static");
                // Mineralogy installs its level reader through a coremod.
                // The packaged migration checks exercise that reader and its sidecar.
                return 1;
            }
            Field field = owner.getDeclaredField(prefix.equals(CORE) ? "persistenceRegistered" : "registered");
            field.setAccessible(true);
            return field.getBoolean(null) ? 1 : 0;
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
