package zone.moddev.mc.skysbuildingpieces;

import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.fixes.BlockStateData;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Supplies the legacy table to plain JUnit. Packaged tests exercise the real mixin. */
public final class LegacyArrayTestTransformer {
    private LegacyArrayTestTransformer() {
    }

    public static byte[] transform(String name, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (name.endsWith("/BlockStateData")) {
            expandTables(node);
        } else {
            var accessor = node.methods.stream().filter(method ->
                    method.name.equals("skysbuildingpieces$getLegacyStateMap")).findFirst().orElseThrow();
            InsnList code = new InsnList();
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "zone/moddev/mc/skysbuildingpieces/LegacyArrayTestTransformer",
                    "readTestTable", "()[Lcom/mojang/serialization/Dynamic;", false));
            code.add(new InsnNode(Opcodes.ARETURN));
            accessor.instructions = code;
            accessor.tryCatchBlocks.clear();
            accessor.localVariables = null;
            accessor.maxStack = 1;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void expandTables(ClassNode node) {
        var initializer = node.methods.stream().filter(method -> method.name.equals("<clinit>"))
                .findFirst().orElseThrow();
        int arrays = 0;
        for (var instruction : initializer.instructions) {
            if (!(instruction instanceof TypeInsnNode type)
                    || instruction.getOpcode() != Opcodes.ANEWARRAY
                    || !type.desc.equals("com/mojang/serialization/Dynamic")) {
                continue;
            }
            int oldSize = arrays == 0 ? 4096 : 256;
            if (arrays > 1 || !(instruction.getPrevious() instanceof IntInsnNode size)
                    || size.operand != oldSize) {
                throw new IllegalStateException("The native test state tables have changed");
            }
            initializer.instructions.set(size, new LdcInsnNode(arrays++ == 0 ? 65_536 : 4_096));
        }
        if (arrays != 2) {
            throw new IllegalStateException("The native test state tables are missing");
        }
    }

    public static Dynamic<?>[] readTestTable() throws ReflectiveOperationException {
        var field = BlockStateData.class.getDeclaredField("MAP");
        field.setAccessible(true);
        return (Dynamic<?>[]) field.get(null);
    }
}
