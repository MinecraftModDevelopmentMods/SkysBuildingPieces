package zone.moddev.mc.skysbuildingpieces.legacy;

import com.mojang.serialization.Dynamic;
import cpw.mods.modlauncher.api.INameMappingService;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.datafix.fixes.BlockStateFlatteningMap;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Source-world numbers are independent of the registry numbers in the running game. */
final class NumericStateBridge {
    static final String NATIVE_STATE_ID = "skysbuildingpieces_native_state";
    private static final BitSet occupied = new BitSet(4096);
    private static final BitSet stagingIds = new BitSet(4096);
    private static final Map<Integer, Dynamic<?>> previous = new LinkedHashMap<>();
    private static final Map<Integer, Dynamic<?>> installed = new LinkedHashMap<>();
    private static final Map<BlockState, Integer> encodings = new LinkedHashMap<>();
    private static final Map<Integer, BlockState> staging = new LinkedHashMap<>();
    private static int openStagingId = -1;
    private static int nextStagingSlot;
    private static boolean nativeNames;

    private NumericStateBridge() {
    }

    static synchronized void begin(Map<Integer, String> snapshot, ListNBT savedStaging) {
        reset();
        occupied.set(0, 256);

        for (int numeric : snapshot.keySet()) {
            if (numeric < 0 || numeric >= 4096) {
                throw new LegacyBridge.RecoveryAbort(
                        "Legacy block number is outside the 12-bit save format: " + numeric, null);
            }

            occupied.set(numeric);
        }
        for (Map.Entry<Integer, String> source : snapshot.entrySet()) {
            PieceBlock block = LegacyBridge.palette(source.getValue());

            if (block != null) {
                int assigned = block.palette.materials.size() * block.palette.shape.states;

                for (int metadata = 0; metadata < assigned; metadata++) {
                    put(source.getKey() << 4 | metadata, zone.moddev.mc.skysbuildingpieces.content.Pieces
                            .canonicalState(block.getStateFromMeta(metadata)));
                }
            }
        }
        for (int index = 0; index < savedStaging.size(); index++) {
            CompoundNBT entry = savedStaging.getCompound(index);
            int encoded = entry.getInt("encoded");
            int blockId = encoded >>> 4;

            if (encoded < 0 || encoded >= 65536 || snapshot.containsKey(blockId) || blockId < 256
                    || staging.containsKey(encoded)) {
                throw new LegacyBridge.RecoveryAbort("Invalid saved migration staging entry: " + encoded, null);
            }

            // Staging survives later ports too. Let an installed catalogue recover a retired native target first.
            CompoundNBT wrapped = new CompoundNBT();

            wrapped.put("BlockState", entry.getCompound("state"));

            BlockState state = NBTUtil.readBlockState(HistoricalItems.prepareCopy(wrapped).getCompound("BlockState"));

            if (state.isAir() || state.hasTileEntity()) {
                throw new LegacyBridge.RecoveryAbort("A saved migration target is no longer available: " + entry, null);
            }

            occupied.set(blockId);
            stagingIds.set(blockId);
            staging.put(encoded, state);
            put(encoded, state);
        }
    }

    static synchronized int encode(BlockState state) {
        Integer prior = encodings.get(state);

        if (prior != null) {
            return prior;
        }
        if (openStagingId < 0) {
            for (int blockId = stagingIds.nextSetBit(0); blockId >= 0; blockId = stagingIds.nextSetBit(blockId + 1)) {
                for (int metadata = 0; metadata < 16; metadata++) {
                    if (!staging.containsKey(blockId << 4 | metadata)) {
                        openStagingId = blockId;
                        nextStagingSlot = metadata;
                        break;
                    }
                }
                if (openStagingId >= 0) {
                    break;
                }
            }
        }
        if (openStagingId < 0 || nextStagingSlot == 16) {
            openStagingId = occupied.nextClearBit(256);

            if (openStagingId >= 4096) {
                throw new LegacyBridge.RecoveryAbort(
                        "No free legacy block numbers remain for safe recovery. "
                                + "Load a backup with the Sky mods in Minecraft 1.12.2 first, then retry this upgrade.",
                        null);
            }

            occupied.set(openStagingId);
            stagingIds.set(openStagingId);
            nextStagingSlot = 0;
        }

        int encoded = openStagingId << 4 | nextStagingSlot++;

        while (nextStagingSlot < 16 && staging.containsKey(openStagingId << 4 | nextStagingSlot)) {
            nextStagingSlot++;
        }

        staging.put(encoded, state);
        put(encoded, state);
        LegacyBridge.persistRegistry();
        return encoded;
    }

    static synchronized ListNBT saveStaging() {
        ListNBT result = new ListNBT();

        for (Map.Entry<Integer, BlockState> entry : staging.entrySet()) {
            CompoundNBT saved = new CompoundNBT();

            saved.putInt("encoded", entry.getKey());
            saved.put("state", NBTUtil.writeBlockState(entry.getValue()));
            result.add(saved);
        }

        return result;
    }

    static synchronized boolean staged(int blockId) {
        return stagingIds.get(blockId);
    }

    static synchronized BlockState stagedState(int encoded) {
        return staging.get(encoded);
    }

    private static void put(int encoded, BlockState state) {
        Dynamic<?>[] table = table();

        if (!previous.containsKey(encoded)) {
            previous.put(encoded, table[encoded]);
        }

        CompoundNBT flattened = NBTUtil.writeBlockState(state);
        preserveNativeName(flattened);
        String name = flattened.getString("Name");

        if (name.equals("minecraft:kelp")) {
            // The later vanilla rename turns kelp_top into today's growing tip; passing today's kelp name here would
            // turn it into a stem instead.
            flattened.putString("Name", "minecraft:kelp_top");
        }
        if (name.matches("minecraft:(oak|spruce|birch|jungle|acacia|dark_oak)_leaves")) {
            // Vanilla's next leaves fixer still reads the old decayable flag. Supply it during numeric flattening so
            // placed leaves stay persistent.
            CompoundNBT properties = flattened.getCompound("Properties");

            properties.putString("decayable", Boolean.toString(!properties.getString("persistent").equals("true")));
        }

        Dynamic<?> value = BlockStateFlatteningMap.parse(flattened.toString());

        table[encoded] = value;
        installed.put(encoded, value);
        encodings.putIfAbsent(state, encoded);
    }

    static synchronized void preserveNativeName(CompoundNBT state) {
        if (state.getString("Name").equals("minecraft:stone_slab")) {
            // This rough-stone slab was added in 1.14. Vanilla still renames the old use of its ID to smooth stone.
            state.putString(NATIVE_STATE_ID, "minecraft:stone_slab");
            nativeNames = true;
        }
    }

    static synchronized boolean needsNativeNames() {
        return nativeNames;
    }

    static synchronized void reset() {
        if (!installed.isEmpty()) {
            Dynamic<?>[] table = table();

            for (Map.Entry<Integer, Dynamic<?>> entry : installed.entrySet()) {
                if (table[entry.getKey()] == entry.getValue()) {
                    table[entry.getKey()] = previous.get(entry.getKey());
                }
            }
        }

        occupied.clear();
        stagingIds.clear();
        previous.clear();
        installed.clear();
        encodings.clear();
        staging.clear();
        openStagingId = -1;
        nextStagingSlot = 0;
        nativeNames = false;
    }

    private static Dynamic<?>[] table() {
        try {
            Field field;

            try {
                field = BlockStateFlatteningMap.class.getDeclaredField("MAP");
            } catch (NoSuchFieldException mappedNameAbsent) {
                field = BlockStateFlatteningMap.class.getDeclaredField(
                        ObfuscationReflectionHelper.remapName(INameMappingService.Domain.FIELD, "field_199200_b"));
            }

            field.setAccessible(true);

            // Clear final before the first reflective read. Java 8 caches its field accessor, including whether that
            // accessor permits a subsequent write.
            Field modifiers = Field.class.getDeclaredField("modifiers");

            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            Dynamic<?>[] current = (Dynamic<?>[]) field.get(null);

            if (current.length >= 65536) {
                return current;
            }

            Dynamic<?>[] expanded = Arrays.copyOf(current, 65536);

            field.set(null, expanded);
            return expanded;
        } catch (ReflectiveOperationException failure) {
            throw new LegacyBridge.RecoveryAbort("Cannot prepare the legacy block-state table", failure);
        }
    }
}
