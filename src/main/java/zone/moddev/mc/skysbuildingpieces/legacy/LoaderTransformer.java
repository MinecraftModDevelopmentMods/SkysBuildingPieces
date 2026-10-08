package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Validated raw recovery hooks and an exact-catalogue native slab item-use hook. */
public final class LoaderTransformer implements IClassTransformer {
    private static final String BRIDGE="zone/moddev/mc/skysbuildingpieces/legacy/LegacyBridge";
    private static final String PLACEMENT="zone/moddev/mc/skysbuildingpieces/content/NativeSlabPlacement";
    public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null) return null;
        boolean chunk=transformedName.equals("net.minecraft.world.chunk.storage.AnvilChunkLoader");
        boolean item=transformedName.equals("net.minecraft.item.ItemStack");
        boolean placement=transformedName.equals("net.minecraft.item.ItemSlab")||transformedName.equals("net.minecraft.item.ItemBlock");
        if(!chunk&&!item&&!placement) return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        int found=0,placementFound=0;
        for(MethodNode m:node.methods) {
            String mappedName=net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(node.name,m.name,m.desc);
            if(placement&&(m.name.equals("onItemUse")||m.name.equals("func_180614_a")||mappedName.equals("func_180614_a"))) {
                ++placementFound;
                addPlacementHook(m);
                continue;
            }
            boolean target=chunk ? m.name.equals("checkedReadChunkFromNBT__Async") : item&&
                (m.name.equals("readFromNBT") || m.name.equals("func_77963_c") || mappedName.equals("func_77963_c")) && Type.getArgumentTypes(m.desc).length==1;
            if(!target) continue;
            found++;
            if(hasHook(m,BRIDGE))continue;
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
        if(!placement&&found!=1) throw new IllegalStateException("Sky legacy recovery cannot find the expected Forge " + transformedName + " hook: " + found);
        if(placement&&placementFound!=1)throw new IllegalStateException("Sky slab placement cannot find the expected item-use hook: "+placementFound);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static boolean hasHook(MethodNode method,String owner) {
        for(AbstractInsnNode instruction:method.instructions.toArray())
            if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).owner.equals(owner))return true;
        return false;
    }
    private static void addPlacementHook(MethodNode method) {
        String descriptor=net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(method.desc);
        if((method.access&Opcodes.ACC_STATIC)!=0||!descriptor.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/EnumActionResult;"))
            throw new IllegalStateException("Unexpected Forge item-use structure");
        if(hasHook(method,PLACEMENT))return;
        InsnList hook=new InsnList();
        for(int local=1;local<=6;local++)hook.add(new VarInsnNode(Opcodes.ALOAD,local));
        for(int local=7;local<=9;local++)hook.add(new VarInsnNode(Opcodes.FLOAD,local));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,PLACEMENT,"place",descriptor,false));
        LabelNode original=new LabelNode();
        hook.add(new InsnNode(Opcodes.DUP));hook.add(new JumpInsnNode(Opcodes.IFNULL,original));hook.add(new InsnNode(Opcodes.ARETURN));
        hook.add(original);hook.add(new FrameNode(Opcodes.F_SAME1,0,null,1,new Object[]{"net/minecraft/util/EnumActionResult"}));hook.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(hook);
    }
}
