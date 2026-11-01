var IntInsnNode = Java.type('org.objectweb.asm.tree.IntInsnNode');
var LdcInsnNode = Java.type('org.objectweb.asm.tree.LdcInsnNode');
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

function previousOpcode(instruction) {
    var current = instruction.getPrevious();
    while (current !== null && current.getOpcode() < 0) {
        current = current.getPrevious();
    }
    return current;
}

function integerValue(instruction) {
    if (instruction instanceof IntInsnNode) {
        return instruction.operand;
    }
    if (instruction instanceof LdcInsnNode && typeof instruction.cst === 'number') {
        return instruction.cst;
    }
    return -1;
}

function replacementFor(size) {
    // LDC also prevents older compatible transformers from mistaking the
    // already-expanded 4,096-entry auxiliary table for the primary table.
    return new LdcInsnNode(size);
}


function initializeCoreMod() {
    return {
        'skysbuildingpieces_legacy_block_state_tables': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.util.datafix.fixes.BlockStateData'
            },
            'transformer': function(classNode) {
                var registerExposed = false;
                var arraysSeen = 0;
                for (var methodIndex = 0; methodIndex < classNode.methods.size(); ++methodIndex) {
                    var method = classNode.methods.get(methodIndex);
                    if (method.desc === '(ILjava/lang/String;[Ljava/lang/String;)V') {
                        method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED))
                                | Opcodes.ACC_PUBLIC;
                        registerExposed = true;
                    }
                    if (method.name !== '<clinit>') {
                        continue;
                    }
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() !== Opcodes.ANEWARRAY ||
                                instruction.desc !== 'com/mojang/serialization/Dynamic') {
                            continue;
                        }
                        var target = arraysSeen === 0 ? 65536 : arraysSeen === 1 ? 4096 : -1;
                        var vanilla = arraysSeen === 0 ? 4096 : arraysSeen === 1 ? 256 : -1;
                        var sizeInstruction = previousOpcode(instruction);
                        var current = integerValue(sizeInstruction);
                        if (target < 0 || (current !== vanilla && current !== target)) {
                            throw new Error("Sky's Building Pieces found an unexpected Forge 37 " +
                                    "BlockStateData array layout");
                        }
                        if (current === vanilla || !(sizeInstruction instanceof LdcInsnNode)) {
                            method.instructions.set(sizeInstruction, replacementFor(target));
                        }
                        ++arraysSeen;
                    }
                }
                if (!registerExposed || arraysSeen !== 2) {
                    throw new Error("Sky's Building Pieces could not prepare Forge 37 BlockStateData");
                }
                return classNode;
            }
        },
        'skysbuildingpieces_material_hardness': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase'},
            'transformer': function(type) {
                var matches = 0;
                var owner = 'zone/moddev/mc/skysbuildingpieces/compat/NativeStateHooks';
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if ((method.name !== 'getDestroySpeed' && method.name !== 'm_60800_')
                            || method.desc !== '(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F') {
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
                                    '(FLnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;'
                                    + 'Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F', false));
                            method.instructions.insertBefore(instruction, hook);
                            returns++;
                        }
                    }
                    if (returns === 0) throw new Error("Sky's Building Pieces: native hardness method has no return");
                    method.maxStack = Math.max(method.maxStack, 4);
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one Forge 37 hardness method, found " + matches);
                }
                return type;
            }
        },
        'skysbuildingpieces_legacy_chunk': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.world.level.chunk.storage.ChunkStorage'},
            'transformer': function(type) {
                var matches = 0;
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if (method.desc !== '(Lnet/minecraft/resources/ResourceKey;'
                            + 'Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundTag;)'
                            + 'Lnet/minecraft/nbt/CompoundTag;') {
                        continue;
                    }
                    matches++;
                    if (hasCall(method, bridge, 'prepareLegacyChunk')) {
                        continue;
                    }
                    var entry = new InsnList();
                    entry.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, bridge, 'prepareLegacyChunk',
                            '(Lnet/minecraft/nbt/CompoundTag;)V', false));
                    method.instructions.insert(entry);
                    var returns = 0;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() === Opcodes.ARETURN) {
                            method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    bridge, 'finalizeLegacyChunk', '(Lnet/minecraft/nbt/CompoundTag;)'
                                    + 'Lnet/minecraft/nbt/CompoundTag;', false));
                            returns++;
                        }
                    }
                    if (returns === 0) {
                        throw new Error("Sky's Building Pieces: Forge 37 chunk upgrader has no return");
                    }
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one Forge 37 chunk upgrader, found " + matches);
                }
                return type;
            }
        },
        'skysbuildingpieces_raw_inventory': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.nbt.NbtUtils'},
            'transformer': function(type) {
                var matches = 0;
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if ((method.access & Opcodes.ACC_STATIC) === 0
                            || method.desc !== '(Lcom/mojang/datafixers/DataFixer;'
                            + 'Lnet/minecraft/util/datafix/DataFixTypes;'
                            + 'Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;') {
                        continue;
                    }
                    matches++;
                    if (!hasCall(method, bridge, 'prepareNbt')) {
                        var entry = new InsnList();
                        entry.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, bridge, 'prepareNbt',
                                '(Lnet/minecraft/nbt/CompoundTag;)V', false));
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
                                        bridge, 'finalizeNbt', '(Lnet/minecraft/nbt/CompoundTag;I)'
                                        + 'Lnet/minecraft/nbt/CompoundTag;', false));
                                method.instructions.insertBefore(instruction, finish);
                                returns++;
                            }
                        }
                        if (returns === 0) {
                            throw new Error("Sky's Building Pieces: Forge 37 NBT updater has no return");
                        }
                    }
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one Forge 37 NBT updater, found " + matches);
                }
                return type;
            }
        },
        'skysbuildingpieces_native_slab_placement': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.world.item.BlockItem'},
            'transformer': function(type) {
                var placement = 'zone/moddev/mc/skysbuildingpieces/content/NativeSlabPlacement';
                var matches = 0;
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if (method.desc !== '(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;') {
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
                            '(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;', false));
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
