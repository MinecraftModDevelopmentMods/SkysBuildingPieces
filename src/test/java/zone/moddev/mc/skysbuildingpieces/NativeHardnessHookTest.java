package zone.moddev.mc.skysbuildingpieces;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

class NativeHardnessHookTest {
    private ScriptEngine engine() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull(engine);
        engine.eval(new String(Files.readAllBytes(Paths.get("src/main/resources/coremods/skysbuildingpieces.js")),
                StandardCharsets.UTF_8));
        return engine;
    }

    private ClassNode nativeState() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase.class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private void apply(ScriptEngine engine, ClassNode node) throws Exception {
        engine.put("candidate", node);
        engine.eval("initializeCoreMod()['skysbuildingpieces_material_hardness'].transformer(candidate)");
    }

    @Test
    void nativeGetterHasOneValidIdempotentHookWithoutChangingOtherFloatGetters() throws Exception {
        ClassNode node = nativeState();
        ScriptEngine engine = engine();
        apply(engine, node);
        int hooks = 0;
        int returns = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.equals("zone/moddev/mc/skysbuildingpieces/compat/NativeStateHooks")) {
                        assertEquals("getDestroySpeed", method.name);
                        assertEquals("destroySpeed", call.name);
                        hooks++;
                    }
                }
                if (method.name.equals("getDestroySpeed") && instruction.getOpcode() == Opcodes.FRETURN) returns++;
            }
            if (method.name.equals("getDestroySpeed")) {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, method);
            }
        }
        assertEquals(1, returns);
        assertEquals(returns, hooks);
        int size = node.methods.stream().mapToInt(method -> method.instructions.size()).sum();
        apply(engine, node);
        assertEquals(size, node.methods.stream().mapToInt(method -> method.instructions.size()).sum());
    }

    @Test
    void missingOrChangedNativeGetterStopsLoading() throws Exception {
        assertThrows(ScriptException.class, () -> apply(engine(), new ClassNode()));
        ClassNode changed = nativeState();
        changed.methods.stream().filter(method -> method.name.equals("getDestroySpeed"))
                .forEach(method -> method.name = "unexpectedGetter");
        assertThrows(ScriptException.class, () -> apply(engine(), changed));
    }
}
