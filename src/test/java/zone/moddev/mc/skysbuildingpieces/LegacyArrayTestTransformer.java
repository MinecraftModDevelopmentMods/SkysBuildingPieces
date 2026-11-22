package zone.moddev.mc.skysbuildingpieces;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** Gives staging tests the same vanilla table layout as a normal Forge launch. */
public final class LegacyArrayTestTransformer {
    private LegacyArrayTestTransformer() {
    }

    public static byte[] transform(byte[] bytes) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        if (engine == null) {
            throw new IllegalStateException("Forge's script engine is missing from the test worker");
        }
        engine.eval(Files.readString(Path.of("src/main/resources/coremods/skysbuildingpieces.js")));
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        engine.put("candidate", node);
        engine.eval("initializeCoreMod()['skysbuildingpieces_legacy_block_state_tables'].transformer(candidate)");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
