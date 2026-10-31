package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** Applies the actual pinned scripts to native bytecode in both mod orders. */
public final class CoremodOrderChecks {
    public static void main(String[] arguments) throws Exception {
        String sky = new String(Files.readAllBytes(Paths.get(arguments[0])), StandardCharsets.UTF_8);
        String mineralogy = script(arguments[1], "mineralogy");
        String grass = script(arguments[2], "skysgrassslabs");
        List<String> scripts = Arrays.asList(sky, mineralogy, grass);

        for (boolean reverse : new boolean[] { false, true }) {
            ClassNode node = new ClassNode();

            try (InputStream input = CoremodOrderChecks.class.getClassLoader()
                    .getResourceAsStream("net/minecraft/world/chunk/storage/ChunkLoader.class")) {
                require(input != null, "Native Forge chunk storage is missing");
                new ClassReader(input).accept(node, 0);
            }
            for (int index = 0; index < scripts.size(); index++) {
                apply(scripts.get(reverse ? scripts.size() - index - 1 : index), node);
            }

            int before = node.methods.stream().mapToInt(method -> method.instructions.size()).sum();

            apply(sky, node);
            require(before == node.methods.stream().mapToInt(method -> method.instructions.size()).sum(),
                    "Repeated Sky transformation changes the combined method");

            int methods = 0;

            for (MethodNode method : node.methods) {
                if (!method.desc.contains("Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundNBT;)")) {
                    continue;
                }

                methods++;

                int returns = 0;

                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.ARETURN) {
                        returns++;
                    }
                }

                require(returns > 0, "Native upgrader must return a chunk");

                for (String namespace : new String[] { "skysbuildingpieces", "mineralogy", "skysgrassslabs" }) {
                    require(calls(method, namespace, "prepareLegacyChunk") == 1,
                            "Missing or repeated entry hook: " + namespace);
                    require(calls(method, namespace, "finalizeLegacyChunk") == returns,
                            "Every return needs both mods' finalizers: " + namespace);
                }

                new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
            }

            require(methods == 1, "Expected one native chunk-upgrade method");
        }

        System.out.println("BUILDING_PIECES_COREMOD_ORDER_PASS orders=2 actual_scripts=true stack_verification=true");
    }

    private static String script(String file, String namespace) throws Exception {
        try (ZipFile archive = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> entries = archive.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.getName().startsWith("coremods/") && entry.getName().endsWith(".js")) {
                    try (InputStream input = archive.getInputStream(entry)) {
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];

                        for (int count; (count = input.read(buffer)) >= 0;) {
                            bytes.write(buffer, 0, count);
                        }

                        String script = new String(bytes.toByteArray(), StandardCharsets.UTF_8);

                        if (script.contains("prepareLegacyChunk") && script.contains(namespace)) {
                            return script;
                        }
                    }
                }
            }
        }

        throw new AssertionError("Pinned artifact has no chunk-upgrade script: " + namespace);
    }

    private static void apply(String script, ClassNode node) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

        require(engine != null, "These native transformers run under Java 8");
        engine.eval(script);
        engine.put("candidate", node);

        Object count = engine.eval("var definitions = initializeCoreMod(); var matches = 0;"
                + "for (var key in definitions) { var definition = definitions[key];"
                + "if (definition.target.type === 'CLASS' && definition.target.name ==="
                + "'net.minecraft.world.chunk.storage.ChunkLoader') {"
                + "definition.transformer(candidate); matches++; }} matches;");

        require(((Number) count).intValue() == 1, "Expected one chunk transformer in the pinned script");
    }

    private static int calls(MethodNode method, String namespace, String name) {
        int calls = 0;

        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;

                if (call.owner.startsWith("zone/moddev/mc/" + namespace + "/") && call.name.equals(name)) {
                    calls++;
                }
            }
        }

        return calls;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
