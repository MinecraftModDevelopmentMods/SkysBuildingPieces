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
        'skysbuildingpieces_sparse_biome_palette': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.world.level.chunk.PalettedContainer$Strategy$2'
            },
            'transformer': function(classNode) {
                var owner = 'zone/moddev/mc/skysbuildingpieces/legacy/SparseBiomeIds';
                var methods = 0;
                var replacements = 0;
                for (var index = 0; index < classNode.methods.size(); index++) {
                    var method = classNode.methods.get(index);
                    if (method.desc !== '(Lnet/minecraft/core/IdMap;I)'
                            + 'Lnet/minecraft/world/level/chunk/PalettedContainer$Configuration;') {
                        continue;
                    }
                    methods++;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (!(instruction instanceof MethodInsnNode)) continue;
                        var original = instruction.getOpcode() === Opcodes.INVOKEINTERFACE
                                && instruction.owner === 'net/minecraft/core/IdMap' && instruction.desc === '()I';
                        var patched = instruction.getOpcode() === Opcodes.INVOKESTATIC
                                && instruction.owner === owner && instruction.name === 'storageSize'
                                && instruction.desc === '(Lnet/minecraft/core/IdMap;)I';
                        if (!original && !patched) continue;
                        var next = instruction.getNext();
                        while (next !== null && next.getOpcode() < 0) next = next.getNext();
                        if (!(next instanceof MethodInsnNode) || next.getOpcode() !== Opcodes.INVOKESTATIC
                                || next.owner !== 'net/minecraft/util/Mth' || next.desc !== '(I)I') {
                            throw new Error("Sky's Building Pieces found an unexpected biome palette width calculation");
                        }
                        replacements++;
                        if (original) {
                            var corrected = new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    owner, 'storageSize', '(Lnet/minecraft/core/IdMap;)I', false);
                            method.instructions.set(instruction, corrected);
                            instruction = corrected;
                        }
                    }
                }
                if (methods !== 1 || replacements !== 1) {
                    throw new Error("Sky's Building Pieces could not prepare the Forge 50 biome palette width");
                }
                return classNode;
            }
        },
        'skysbuildingpieces_legacy_level_registry': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraftforge.common.ForgeHooks'
            },
            'transformer': function(classNode) {
                var readMethods = 0;
                var writeMethods = 0;
                var owner = 'zone/moddev/mc/skysbuildingpieces/legacy/LegacyBridge';
                for (var index = 0; index < classNode.methods.size(); ++index) {
                    var method = classNode.methods.get(index);
                    var reading = method.name === 'readAdditionalLevelSaveData' &&
                            method.desc === '(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelDirectory;)V';
                    var writing = method.name === 'writeAdditionalLevelSaveData' &&
                            method.desc === '(Lnet/minecraft/world/level/storage/WorldData;' +
                                    'Lnet/minecraft/nbt/CompoundTag;)V';
                    if (!reading && !writing) continue;
                    if (reading) ++readMethods;
                    else ++writeMethods;
                    var callback = reading ? 'captureLegacyLevelData' : 'writeLegacyLevelData';
                    var existingCalls = 0;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction instanceof MethodInsnNode && instruction.owner === owner &&
                                instruction.name === callback) ++existingCalls;
                    }
                    if (existingCalls > 1) {
                        throw new Error("Sky's Building Pieces found duplicate Forge 50 level hooks");
                    }
                    if (existingCalls === 1) continue;
                    var prefix = new InsnList();
                    prefix.add(new VarInsnNode(Opcodes.ALOAD, reading ? 0 : 1));
                    if (reading) prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, callback,
                            reading ? '(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelDirectory;)V' :
                                    '(Lnet/minecraft/nbt/CompoundTag;)V', false));
                    method.instructions.insert(prefix);
                }
                if (readMethods !== 1 || writeMethods !== 1) {
                    throw new Error("Sky's Building Pieces could not find the exact Forge 50 level methods");
                }
                return classNode;
            }
        },
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
                            throw new Error("Sky's Building Pieces found an unexpected Forge 50 " +
                                    "BlockStateData array layout");
                        }
                        if (current === vanilla || !(sizeInstruction instanceof LdcInsnNode)) {
                            method.instructions.set(sizeInstruction, replacementFor(target));
                        }
                        ++arraysSeen;
                    }
                }
                if (!registerExposed || arraysSeen !== 2) {
                    throw new Error("Sky's Building Pieces could not prepare Forge 50 BlockStateData");
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
                    throw new Error("Sky's Building Pieces: expected one Forge 50 hardness method, found " + matches);
                }
                return type;
            }
        },
        'skysbuildingpieces_legacy_chunk': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.world.level.chunk.storage.ChunkStorage'
            },
            'transformer': function(classNode) {
                var patched = false;
                var matches = 0;
                for (var methodIndex = 0; methodIndex < classNode.methods.size(); ++methodIndex) {
                    var method = classNode.methods.get(methodIndex);
                    if (method.desc !== '(Lnet/minecraft/resources/ResourceKey;' +
                            'Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundTag;Ljava/util/Optional;)' +
                            'Lnet/minecraft/nbt/CompoundTag;') {
                        continue;
                    }

                    ++matches;
                    var owner = 'zone/moddev/mc/skysbuildingpieces/legacy/LegacyBridge';
                    var preparations = 0;
                    var finalizations = 0;
                    var returns = 0;
                    for (var existing = method.instructions.getFirst(); existing !== null;
                            existing = existing.getNext()) {
                        if (existing.getOpcode() === Opcodes.ARETURN) {
                            ++returns;
                        }
                        if (existing instanceof MethodInsnNode && existing.owner === owner) {
                            if (existing.name === 'prepareLegacyChunk') ++preparations;
                            if (existing.name === 'finalizeLegacyChunk') ++finalizations;
                        }
                    }
                    if (returns === 0) {
                        throw new Error("Sky's Building Pieces found no Forge 50 chunk return");
                    }
                    if (preparations !== 0 || finalizations !== 0) {
                        if (preparations !== 1 || finalizations !== returns) {
                            throw new Error("Sky's Building Pieces found a partial Forge 50 chunk patch");
                        }
                        patched = true;
                        continue;
                    }

                    var prefix = new InsnList();
                    prefix.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    prefix.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            owner,
                            'prepareLegacyChunk',
                            '(Lnet/minecraft/nbt/CompoundTag;)V',
                            false));
                    method.instructions.insert(prefix);

                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() === Opcodes.ARETURN) {
                            method.instructions.insertBefore(instruction, new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    owner,
                                    'finalizeLegacyChunk',
                                    '(Lnet/minecraft/nbt/CompoundTag;)' +
                                    'Lnet/minecraft/nbt/CompoundTag;',
                                    false));
                        }
                    }
                    patched = true;
                }
                if (!patched || matches !== 1) {
                    throw new Error("Sky's Building Pieces could not patch Forge 50 ChunkStorage");
                }
                return classNode;
            }
        },
        'skysbuildingpieces_raw_inventory': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.util.datafix.DataFixTypes'},
            'transformer': function(type) {
                var matches = 0;
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if ((method.access & Opcodes.ACC_STATIC) !== 0
                            || method.desc !== '(Lcom/mojang/datafixers/DataFixer;'
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
                            throw new Error("Sky's Building Pieces: Forge 50 NBT updater has no return");
                        }
                    }
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces: expected one Forge 50 NBT updater, found " + matches);
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
