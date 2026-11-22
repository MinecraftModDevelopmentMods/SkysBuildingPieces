package zone.moddev.mc.skysbuildingpieces.probe.legacy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.BlockState;

/** Accesses private staging checks without sharing a package across Forge modules. */
final class NumericStateBridge {
    private NumericStateBridge() {
    }

    static void begin(Map<Integer, String> snapshot, ListTag staging) {
        invoke("begin", new Class<?>[] {Map.class, ListTag.class}, snapshot, staging);
    }

    static int encode(BlockState state) {
        return (Integer) invoke("encode", new Class<?>[] {BlockState.class}, state);
    }

    static ListTag saveStaging() {
        return (ListTag) invoke("saveStaging", new Class<?>[0]);
    }

    static BlockState stagedState(int encoded) {
        return (BlockState) invoke("stagedState", new Class<?>[] {int.class}, encoded);
    }

    private static Object invoke(String name, Class<?>[] parameters, Object... arguments) {
        try {
            Class<?> owner = Class.forName("zone.moddev.mc.skysbuildingpieces.legacy.NumericStateBridge");
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException) {
                throw (RuntimeException) failure.getCause();
            }
            throw new IllegalStateException("The native staging check failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot reach the native staging check: " + name, failure);
        }
    }
}
