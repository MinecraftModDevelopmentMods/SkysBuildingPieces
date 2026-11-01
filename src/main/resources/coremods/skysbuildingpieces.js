var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var bridge = 'zone/moddev/mc/skysbuildingpieces/legacy/LegacyBridge';

function hasCall(method, owner, name) {
    for (var instruction = method.instructions.getFirst(); instruction !== null;
            instruction = instruction.getNext()) {
        if (instruction.getOpcode() === Opcodes.INVOKESTATIC
                && instruction.owner === owner && instruction.name === name) {
            return true;
        }
    }
    return false;
}

function initializeCoreMod() {
    return {
        'skysbuildingpieces_material_hardness': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.block.AbstractBlock$AbstractBlockState'},
            'transformer': function(type) {
                var matches = 0;
                var owner = 'zone/moddev/mc/skysbuildingpieces/compat/NativeStateHooks';
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if ((method.name !== 'getDestroySpeed' && method.name !== 'func_185887_b')
                            || method.desc !== '(Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;)F') {
                        continue;
                    }
                    matches++;
                    if (hasCall(method, owner, 'destroySpeed')) continue;
                    var returns = 0;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() === Opcodes.FRETURN) {
                            var hook = new InsnList();
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, 'destroySpeed',
                                    '(FLnet/minecraft/block/AbstractBlock$AbstractBlockState;'
                                    + 'Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;)F', false));
                            method.instructions.insertBefore(instruction, hook);
                            returns++;
                        }
                    }
                    if (returns === 0) throw new Error("Sky's Building Pieces: native hardness method has no return");
                    method.maxStack = Math.max(method.maxStack, 4);
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one Forge 36 hardness method, found " + matches);
                }
                return type;
            }
        },
        'skysbuildingpieces_legacy_chunk': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.world.chunk.storage.ChunkLoader'},
            'transformer': function(type) {
                var matches = 0;
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if (method.desc !== '(Lnet/minecraft/util/RegistryKey;'
                            + 'Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundNBT;)'
                            + 'Lnet/minecraft/nbt/CompoundNBT;') {
                        continue;
                    }
                    matches++;
                    if (hasCall(method, bridge, 'prepareLegacyChunk')) {
                        continue;
                    }
                    var entry = new InsnList();
                    entry.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, bridge, 'prepareLegacyChunk',
                            '(Lnet/minecraft/nbt/CompoundNBT;)V', false));
                    method.instructions.insert(entry);
                    var returns = 0;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() === Opcodes.ARETURN) {
                            method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    bridge, 'finalizeLegacyChunk', '(Lnet/minecraft/nbt/CompoundNBT;)'
                                    + 'Lnet/minecraft/nbt/CompoundNBT;', false));
                            returns++;
                        }
                    }
                    if (returns === 0) {
                        throw new Error("Sky's Building Pieces: Forge 36 chunk upgrader has no return");
                    }
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one Forge 36 chunk upgrader, found " + matches);
                }
                return type;
            }
        },
        'skysbuildingpieces_raw_inventory': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.nbt.NBTUtil'},
            'transformer': function(type) {
                var matches = 0;
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if ((method.access & Opcodes.ACC_STATIC) === 0
                            || method.desc !== '(Lcom/mojang/datafixers/DataFixer;'
                            + 'Lnet/minecraft/util/datafix/DefaultTypeReferences;'
                            + 'Lnet/minecraft/nbt/CompoundNBT;II)Lnet/minecraft/nbt/CompoundNBT;') {
                        continue;
                    }
                    matches++;
                    if (!hasCall(method, bridge, 'prepareNbt')) {
                        var entry = new InsnList();
                        entry.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, bridge, 'prepareNbt',
                                '(Lnet/minecraft/nbt/CompoundNBT;)V', false));
                        method.instructions.insert(entry);
                    }
                    if (!hasCall(method, bridge, 'finalizeNbt')) {
                        var returns = 0;
                        for (var instruction = method.instructions.getFirst(); instruction !== null;
                                instruction = instruction.getNext()) {
                            if (instruction.getOpcode() === Opcodes.ARETURN) {
                                var finish = new InsnList();
                                finish.add(new VarInsnNode(Opcodes.ILOAD, 4));
                                finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                        bridge, 'finalizeNbt', '(Lnet/minecraft/nbt/CompoundNBT;I)'
                                        + 'Lnet/minecraft/nbt/CompoundNBT;', false));
                                method.instructions.insertBefore(instruction, finish);
                                returns++;
                            }
                        }
                        if (returns === 0) {
                            throw new Error("Sky's Building Pieces: Forge 36 NBT updater has no return");
                        }
                    }
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one Forge 36 NBT updater, found " + matches);
                }
                return type;
            }
        },
        'skysbuildingpieces_native_slab_placement': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.item.BlockItem'},
            'transformer': function(type) {
                var placement = 'zone/moddev/mc/skysbuildingpieces/content/NativeSlabPlacement';
                var matches = 0;
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if (method.desc !== '(Lnet/minecraft/item/ItemUseContext;)Lnet/minecraft/util/ActionResultType;') {
                        continue;
                    }
                    matches++;
                    if (hasCall(method, placement, 'place')) {
                        continue;
                    }
                    var original = new LabelNode();
                    var entry = new InsnList();
                    entry.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, placement, 'place',
                            '(Lnet/minecraft/item/ItemUseContext;)Lnet/minecraft/util/ActionResultType;', false));
                    entry.add(new InsnNode(Opcodes.DUP));
                    entry.add(new JumpInsnNode(Opcodes.IFNULL, original));
                    entry.add(new InsnNode(Opcodes.ARETURN));
                    entry.add(original);
                    entry.add(new InsnNode(Opcodes.POP));
                    method.instructions.insert(entry);
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one native block-item use method, found " + matches);
                }
                return type;
            }
        }
    };
}
