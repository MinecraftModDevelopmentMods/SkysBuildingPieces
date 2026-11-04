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

/** Applies the three pinned scripts in every order to the actual target classes. */
public final class CoremodOrderChecks {
    public static void main(String[] arguments) throws Exception {
        String sky = new String(Files.readAllBytes(Paths.get(arguments[0])), StandardCharsets.UTF_8);
        String mineralogy = script(arguments[1], "mineralogy");
        String grass = script(arguments[2], "skysgrassslabs");
        List<String> scripts = Arrays.asList(sky, mineralogy, grass);

        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] order : orders) {
            ClassNode biomePalette = nativeClass("net/minecraft/world/level/chunk/PalettedContainer$Strategy$2");
            for (int index : order) {
                apply(scripts.get(index), biomePalette, index == 0 ? 1 : 0);
            }
            int paletteInstructions = biomePalette.methods.stream().mapToInt(method -> method.instructions.size()).sum();
            apply(sky, biomePalette);
            require(paletteInstructions == biomePalette.methods.stream().mapToInt(method -> method.instructions.size()).sum(),
                    "Repeated Sky transformation changes the biome palette width");
            int widths = 0;
            for (MethodNode method : biomePalette.methods) {
                widths += calls(method, "skysbuildingpieces", "storageSize");
                if (calls(method, "skysbuildingpieces", "storageSize") > 0) {
                    new Analyzer<>(new BasicVerifier()).analyze(biomePalette.name, method);
                }
            }
            require(widths == 1, "Expected one corrected biome storage-width calculation");

            ClassNode tables = nativeClass("net/minecraft/util/datafix/fixes/BlockStateData");
            for (int index : order) {
                apply(scripts.get(index), tables);
            }
            verifyTables(tables);
            int tableInstructions = tables.methods.stream().mapToInt(method -> method.instructions.size()).sum();
            apply(sky, tables);
            verifyTables(tables);
            require(tableInstructions == tables.methods.stream().mapToInt(method -> method.instructions.size()).sum(),
                    "Repeated Sky transformation changes the combined state tables");

            ClassNode node = nativeClass("net/minecraft/world/level/chunk/storage/ChunkStorage");
            for (int index : order) {
                apply(scripts.get(index), node);
            }

            int before = node.methods.stream().mapToInt(method -> method.instructions.size()).sum();

            apply(sky, node);
            require(before == node.methods.stream().mapToInt(method -> method.instructions.size()).sum(),
                    "Repeated Sky transformation changes the combined method");

            int methods = 0;

            for (MethodNode method : node.methods) {
                if (!method.desc.equals("(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Supplier;"
                        + "Lnet/minecraft/nbt/CompoundTag;Ljava/util/Optional;)Lnet/minecraft/nbt/CompoundTag;")) {
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

            ClassNode level = nativeClass("net/minecraftforge/common/ForgeHooks");
            for (int index : order) {
                apply(scripts.get(index), level);
            }
            int levelInstructions = level.methods.stream().mapToInt(method -> method.instructions.size()).sum();
            apply(sky, level);
            require(levelInstructions == level.methods.stream().mapToInt(method -> method.instructions.size()).sum(),
                    "Repeated Sky transformation changes the level wrappers");
            for (String namespace : new String[] {"skysbuildingpieces", "mineralogy", "skysgrassslabs"}) {
                int readers = 0;
                int writers = 0;
                for (MethodNode method : level.methods) {
                    readers += calls(method, namespace, "captureLegacyLevelData");
                    writers += calls(method, namespace, "writeLegacyLevelData");
                    if (calls(method, namespace, "captureLegacyLevelData") > 0
                            || calls(method, namespace, "writeLegacyLevelData") > 0) {
                        new Analyzer<>(new BasicVerifier()).analyze(level.name, method);
                    }
                }
                require(readers == 1,
                        "Unexpected native level capture count: " + namespace);
                require(writers == (namespace.equals("mineralogy") ? 0 : 1),
                        "Missing or repeated level writer: " + namespace);
            }
        }

        ClassNode unsupported = nativeClass("net/minecraft/world/level/chunk/PalettedContainer$Strategy$2");
        unsupported.methods.removeIf(method -> method.desc.startsWith("(Lnet/minecraft/core/IdMap;I)"));
        boolean refused = false;
        try {
            apply(sky, unsupported);
        } catch (javax.script.ScriptException expected) {
            refused = expected.getMessage().contains("biome palette width");
        }
        require(refused, "An unsupported biome strategy must stop with an explanation");
        System.out.println("BUILDING_PIECES_COREMOD_ORDER_PASS orders=6 targets=4 actual_scripts=true stack_verification=true");
    }

    private static ClassNode nativeClass(String name) throws Exception {
        try (InputStream input = CoremodOrderChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            require(input != null, "Native Forge class is missing: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static void verifyTables(ClassNode node) throws Exception {
        int arrays = 0;
        int registrationMethods = 0;
        int[] expected = {65536, 4096};
        for (MethodNode method : node.methods) {
            if (method.desc.equals("(ILjava/lang/String;[Ljava/lang/String;)V")) {
                require((method.access & Opcodes.ACC_PUBLIC) != 0, "Legacy state registration is not accessible");
                registrationMethods++;
            }
            if (!method.name.equals("<clinit>")) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof org.objectweb.asm.tree.TypeInsnNode)
                        || instruction.getOpcode() != Opcodes.ANEWARRAY
                        || !((org.objectweb.asm.tree.TypeInsnNode) instruction).desc.equals("com/mojang/serialization/Dynamic")) {
                    continue;
                }
                AbstractInsnNode size = instruction.getPrevious();
                while (size != null && size.getOpcode() < 0) {
                    size = size.getPrevious();
                }
                require(arrays < expected.length && size instanceof org.objectweb.asm.tree.LdcInsnNode,
                        "Unexpected expanded state table layout");
                Object value = ((org.objectweb.asm.tree.LdcInsnNode) size).cst;
                require(value instanceof Number && ((Number) value).intValue() == expected[arrays],
                        "A companion changed the expanded state table size");
                arrays++;
            }
            new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        }
        require(arrays == 2 && registrationMethods == 1, "Both legacy tables and registration method must survive");
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
        apply(script, node, 1);
    }

    private static void apply(String script, ClassNode node, int expectedMatches) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

        require(engine != null, "Forge's Nashorn script engine is missing from the test runtime");
        engine.eval(script);
        engine.put("candidate", node);
        engine.put("targetName", node.name.replace('/', '.'));

        Object count = engine.eval("var definitions = initializeCoreMod(); var matches = 0;"
                + "for (var key in definitions) { var definition = definitions[key];"
                + "if (definition.target.type === 'CLASS' && String(definition.target.name) === String(targetName)) {"
                + "definition.transformer(candidate); matches++; }} matches;");

        require(((Number) count).intValue() == expectedMatches, "Unexpected matching transformers in the pinned script");
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
