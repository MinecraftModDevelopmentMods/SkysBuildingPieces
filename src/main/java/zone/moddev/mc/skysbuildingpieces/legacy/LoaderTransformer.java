package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Two entry hooks only: raw chunk sections and raw item compounds. */
public final class LoaderTransformer implements IClassTransformer {
    private static final String BRIDGE="zone/moddev/mc/skysbuildingpieces/legacy/LegacyBridge";
    public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null) return null;
        boolean chunk=transformedName.equals("net.minecraft.world.chunk.storage.AnvilChunkLoader");
        boolean item=transformedName.equals("net.minecraft.item.ItemStack");
        if(!chunk&&!item) return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        int found=0;
        for(MethodNode m:node.methods) {
            String mappedName=net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(node.name,m.name,m.desc);
            boolean target=chunk ? m.name.equals("checkedReadChunkFromNBT__Async") :
                (m.name.equals("readFromNBT") || m.name.equals("func_77963_c") || mappedName.equals("func_77963_c")) && Type.getArgumentTypes(m.desc).length==1;
            if(!target) continue;
            found++;
            for(AbstractInsnNode ins:m.instructions.toArray()) if(ins instanceof MethodInsnNode && ((MethodInsnNode)ins).owner.equals(BRIDGE)) return bytes;
            Type[] args=Type.getArgumentTypes(m.desc);
            String descriptor=net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(m.desc);
            if((m.access&Opcodes.ACC_STATIC)!=0 || chunk && (!descriptor.equals("(Lnet/minecraft/world/World;IILnet/minecraft/nbt/NBTTagCompound;)[Ljava/lang/Object;")) ||
                item && !descriptor.equals("(Lnet/minecraft/nbt/NBTTagCompound;)V"))
                throw new IllegalStateException("Unexpected Forge chunk loader structure");
            InsnList hook=new InsnList();
            if(chunk) { hook.add(new VarInsnNode(Opcodes.ALOAD,1)); hook.add(new VarInsnNode(Opcodes.ALOAD,4)); }
            else hook.add(new VarInsnNode(Opcodes.ALOAD,1));
            descriptor=chunk ? "("+args[0].getDescriptor()+args[3].getDescriptor()+")V" : "("+args[0].getDescriptor()+")V";
            descriptor=net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(descriptor);
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,chunk?"prepareChunk":"prepareStack",descriptor,false));
            m.instructions.insert(hook);
        }
        if(found!=1) throw new IllegalStateException("Sky legacy recovery cannot find the expected Forge " + transformedName + " hook: " + found);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
