package zone.moddev.mc.skysbuildingpieces;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class LoaderHooksTest {
    private static Map<String, String> targets() {
        Map<String, String> targets = new LinkedHashMap<>();

        targets.put("skysbuildingpieces_legacy_chunk", "net/minecraft/world/chunk/storage/ChunkLoader");
        targets.put("skysbuildingpieces_raw_inventory", "net/minecraft/nbt/NBTUtil");
        targets.put("skysbuildingpieces_native_slab_placement", "net/minecraft/item/BlockItem");
        return targets;
    }

    private ScriptEngine engine() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

        assertNotNull(engine, "Forge 36 transformers and their tests require the production Java 8 runtime");
        engine.eval(new String(Files.readAllBytes(Paths.get("src/main/resources/coremods/skysbuildingpieces.js")),
                StandardCharsets.UTF_8));
        return engine;
    }

    private ClassNode original(String target) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(target + ".class")) {
            assertNotNull(stream, target);

            ClassNode node = new ClassNode();

            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private void transform(ScriptEngine engine, String key, ClassNode node) throws Exception {
        engine.put("candidate", node);
        engine.eval("initializeCoreMod()['" + key + "'].transformer(candidate)");
    }

    private int calls(ClassNode node, String name) {
        int count = 0;

        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;

                    if (call.owner.startsWith("zone/moddev/mc/skysbuildingpieces/") && call.name.equals(name)) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    @Test
    void realForge36MethodsMatchAndEveryReturnIsWrapped() throws Exception {
        for (Map.Entry<String, String> target : targets().entrySet()) {
            ScriptEngine engine = engine();
            ClassNode node = original(target.getValue());
            int returns = 0;

            if (target.getKey().endsWith("legacy_chunk")) {
                for (MethodNode method : node.methods) {
                    if (method.desc.contains("Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundNBT;)")) {
                        for (AbstractInsnNode instruction : method.instructions.toArray()) {
                            if (instruction.getOpcode() == Opcodes.ARETURN) {
                                returns++;
                            }
                        }
                    }
                }

                assertTrue(returns > 0);
            }

            transform(engine, target.getKey(), node);
            assertEquals(target.getKey().endsWith("legacy_chunk") ? 1 : 0, calls(node, "prepareLegacyChunk"));
            assertEquals(returns, calls(node, "finalizeLegacyChunk"));
            assertEquals(target.getKey().endsWith("raw_inventory") ? 1 : 0, calls(node, "prepareNbt"));

            if (target.getKey().endsWith("raw_inventory")) {
                int inventoryReturns = 0;

                for (MethodNode method : node.methods) {
                    if (method.desc.equals(
                            "(Lcom/mojang/datafixers/DataFixer;" + "Lnet/minecraft/util/datafix/DefaultTypeReferences;"
                                    + "Lnet/minecraft/nbt/CompoundNBT;II)Lnet/minecraft/nbt/CompoundNBT;")) {
                        for (AbstractInsnNode instruction : method.instructions.toArray()) {
                            if (instruction.getOpcode() == Opcodes.ARETURN) {
                                inventoryReturns++;
                            }
                            if (instruction instanceof MethodInsnNode
                                    && ((MethodInsnNode) instruction).name.equals("finalizeNbt")) {
                                MethodInsnNode finish = (MethodInsnNode) instruction;
                                assertEquals("(Lnet/minecraft/nbt/CompoundNBT;I)Lnet/minecraft/nbt/CompoundNBT;",
                                        finish.desc);
                                assertTrue(finish.getPrevious() instanceof VarInsnNode);
                                VarInsnNode version = (VarInsnNode) finish.getPrevious();
                                assertEquals(Opcodes.ILOAD, version.getOpcode());
                                assertEquals(4, version.var, "Forward the requested target version, not the old version");
                            }
                        }
                    }
                }

                assertTrue(inventoryReturns > 0);
                assertEquals(inventoryReturns, calls(node, "finalizeNbt"));
            }

            assertEquals(target.getKey().endsWith("native_slab_placement") ? 1 : 0, calls(node, "place"));

            int instructions = node.methods.stream().mapToInt(method -> method.instructions.size()).sum();

            transform(engine, target.getKey(), node);
            assertEquals(instructions, node.methods.stream().mapToInt(method -> method.instructions.size()).sum(),
                    "A repeated transformation must leave the class unchanged: " + target.getValue());
        }
    }

    @Test
    void missingAndAmbiguousStructuresFailBeforeLoading() throws Exception {
        for (Map.Entry<String, String> target : targets().entrySet()) {
            ClassNode empty = new ClassNode();

            assertThrows(ScriptException.class, () -> transform(engine(), target.getKey(), empty));

            ClassNode node = original(target.getValue());

            transform(engine(), target.getKey(), node);

            MethodNode patched = node.methods.stream().filter(method -> {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode
                            && ((MethodInsnNode) instruction).owner.startsWith("zone/moddev/mc/skysbuildingpieces/")) {
                        return true;
                    }
                }

                return false;
            }).findFirst().get();

            node.methods.add(patched);
            assertThrows(ScriptException.class, () -> transform(engine(), target.getKey(), node));
        }
    }
}
