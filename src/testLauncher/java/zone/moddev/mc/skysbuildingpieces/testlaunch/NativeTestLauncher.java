package zone.moddev.mc.skysbuildingpieces.testlaunch;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Applies the production array patch before the unit JVM loads vanilla data fixing. */
public final class NativeTestLauncher {
    private NativeTestLauncher() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                    ProtectionDomain domain, byte[] bytes) throws IllegalClassFormatException {
                if (!"net/minecraft/util/datafix/fixes/BlockStateData".equals(name)) {
                    return null;
                }
                try {
                    // The test worker supplies ASM and the script engine through its own loader.
                    Class<?> transformer = Class.forName(
                            "zone.moddev.mc.skysbuildingpieces.LegacyArrayTestTransformer", true, loader);
                    return (byte[]) transformer.getMethod("transform", byte[].class).invoke(null, (Object) bytes);
                } catch (ReflectiveOperationException failure) {
                    IllegalClassFormatException error = new IllegalClassFormatException(
                            "Could not apply the production legacy-table patch in the unit JVM");
                    error.initCause(failure);
                    throw error;
                }
            }
        });
    }
}
