package zone.moddev.mc.skysbuildingpieces;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import zone.moddev.mc.skysbuildingpieces.legacy.LoaderTransformer;
import static org.junit.jupiter.api.Assertions.*;

class LoaderHooksTest {
    private byte[] fixture(String name,String method,String descriptor) {
        ClassWriter w=new ClassWriter(0);w.visit(52,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,method,descriptor,null,null);m.visitCode();
        if(Type.getReturnType(descriptor).getSort()==Type.VOID)m.visitInsn(Opcodes.RETURN);else {m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ARETURN);}
        m.visitMaxs(1,5);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    @Test void exactChunkAndItemHooksAreIdempotent() {
        for(boolean chunk:new boolean[]{true,false}) {
            String name=chunk?"net.minecraft.world.chunk.storage.AnvilChunkLoader":"net.minecraft.item.ItemStack";
            byte[] original=fixture(name.replace('.','/'),chunk?"checkedReadChunkFromNBT__Async":"readFromNBT",chunk?"(Lnet/minecraft/world/World;IILnet/minecraft/nbt/NBTTagCompound;)[Ljava/lang/Object;":"(Lnet/minecraft/nbt/NBTTagCompound;)V");
            LoaderTransformer t=new LoaderTransformer();byte[] patched=t.transform(name,name,original);assertArrayEquals(patched,t.transform(name,name,patched));
            ClassNode n=new ClassNode();new ClassReader(patched).accept(n,0);int hooks=0;
            for(MethodNode method:n.methods)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).owner.endsWith("/LegacyBridge"))hooks++;
            assertEquals(1,hooks);
        }
    }
    @Test void unexpectedLoaderStructuresFailClearly() {
        String name="net.minecraft.world.chunk.storage.AnvilChunkLoader";
        assertThrows(IllegalStateException.class,()->new LoaderTransformer().transform(name,name,fixture(name.replace('.','/'),"checkedReadChunkFromNBT__Async","(Lnet/minecraft/world/World;IILjava/lang/Object;)[Ljava/lang/Object;")));
        assertThrows(IllegalStateException.class,()->new LoaderTransformer().transform(name,name,fixture(name.replace('.','/'),"unexpectedMethod","()V")));
    }
}
