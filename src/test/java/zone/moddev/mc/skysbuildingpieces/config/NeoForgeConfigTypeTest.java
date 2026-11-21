package zone.moddev.mc.skysbuildingpieces.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import net.neoforged.fml.config.ModConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class NeoForgeConfigTypeTest {
    private static final String HELPER =
            "zone.moddev.mc.skysbuildingpieces.config.NeoForgeConfigType";

    @TempDir
    Path temporary;

    @Test
    public void actualLoaderUsesItsUnsyncedConfigType() throws Exception {
        String expected = java.util.Arrays.stream(ModConfig.Type.values())
                .anyMatch(type -> type.name().equals("LOCAL")) ? "LOCAL" : "COMMON";
        Method local = Class.forName(HELPER).getDeclaredMethod("local");
        local.setAccessible(true);
        assertEquals(ModConfig.Type.valueOf(expected), local.invoke(null));
    }

    @Test
    public void olderLoaderKeepsCommonScope() throws Exception {
        assertEquals("COMMON", resolveWith("COMMON, CLIENT, SERVER, STARTUP"));
    }

    @Test
    public void newerLoaderUsesLocalScope() throws Exception {
        assertEquals("LOCAL", resolveWith("LOCAL, CLIENT, SYNCED, STARTUP"));
    }

    @Test
    public void localWinsWhenBothNamesExist() throws Exception {
        assertEquals("LOCAL", resolveWith("COMMON, LOCAL, CLIENT, SYNCED, STARTUP"));
    }

    @Test
    public void missingUnsyncedScopeFailsInsteadOfUsingClientOrSynced() throws Exception {
        try {
            resolveWith("CLIENT, SYNCED, STARTUP");
            fail("A loader without either local scope must fail clearly");
        } catch (InvocationTargetException exception) {
            assertTrue(exception.getCause() instanceof IllegalArgumentException);
            assertTrue(exception.getCause().getMessage().contains("COMMON"));
        }
    }

    @Test
    public void registrationRetainsTheExistingConfigFile() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/zone/moddev/mc/skysbuildingpieces/config/BuildingPiecesConfig.java"));
        assertTrue(source.contains("-common.toml"));
        assertTrue(source.contains("registerConfig(NeoForgeConfigType.local(), SPEC, FILE_NAME)"));
    }

    private String resolveWith(String constants) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "enum-");
        Path source = directory.resolve("net/neoforged/fml/config/ModConfig.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package net.neoforged.fml.config; public final class ModConfig {"
                + " public enum Type { " + constants + " } }");
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The enum compatibility test requires a JDK");
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "-d",
                directory.toString(), source.toString()));
        Path helper = directory.resolve(HELPER.replace('.', '/') + ".class");
        Files.createDirectories(helper.getParent());
        try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(
                HELPER.replace('.', '/') + ".class")) {
            assertNotNull(bytes, "The shipped config helper must be present");
            Files.copy(bytes, helper);
        }
        try (URLClassLoader isolated = new URLClassLoader(
                new URL[] {directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Method local = isolated.loadClass(HELPER).getDeclaredMethod("local");
            local.setAccessible(true);
            return ((Enum<?>) local.invoke(null)).name();
        }
    }
}
