package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundNBT;

/** Raw-item recovery and selected native names which must survive vanilla's fixers. */
public final class HistoricalItems {
    public static final String NATIVE_ITEM_ID = "skysbuildingpieces_native_item";
    private static final Registry RECOVERIES = new Registry();
    private static volatile boolean nativeItems;

    private HistoricalItems() {
    }

    public static void register(String module, Predicate<CompoundNBT> relevant, Consumer<CompoundNBT> recovery) {
        if (LegacyBridge.ready) {
            throw new IllegalStateException("Item recovery registration has finished");
        }

        RECOVERIES.register(module, relevant, recovery);
    }

    /** No copy or traversal is added to standalone runs without contributors. */
    static CompoundNBT prepareCopy(CompoundNBT root) {
        return RECOVERIES.prepareCopy(root);
    }

    static void finish(CompoundNBT root, int targetVersion) {
        // ChunkLoader first fixes old chunks to 1493, then to the current version. Keep selected names through that
        // intermediate tag, since the stone slab rename runs at 1802.
        if (targetVersion < 1802) {
            return;
        }
        if (RECOVERIES.hasContributors() || nativeItems || NumericStateBridge.needsNativeNames()) {
            restoreNativeTargets(root);
        }
    }

    static void preserveNativeItem(CompoundNBT stack) {
        String name = stack.getString("id");
        if (name.equals("minecraft:stone_slab") || name.equals("minecraft:stone_stairs")) {
            stack.putString(NATIVE_ITEM_ID, name);
            nativeItems = true;
        }
    }

    static void clearNativeNames() {
        nativeItems = false;
    }

    static void restoreNativeTargets(CompoundNBT root) {
        List<CompoundNBT> stacks = new ArrayList<>();
        List<CompoundNBT> states = new ArrayList<>();

        collectNativeTargets(root, stacks, states, 0);

        for (CompoundNBT stack : stacks) {
            stack.putString("id", stack.getString(NATIVE_ITEM_ID));
            stack.remove(NATIVE_ITEM_ID);
        }
        for (CompoundNBT state : states) {
            state.putString("Name", state.getString(NumericStateBridge.NATIVE_STATE_ID));
            state.remove(NumericStateBridge.NATIVE_STATE_ID);
        }
    }

    private static void collectNativeTargets(net.minecraft.nbt.INBT value, List<CompoundNBT> stacks,
            List<CompoundNBT> states, int depth) {
        if (depth > 64) {
            throw new LegacyBridge.RecoveryAbort("Saved native item nesting exceeds 64 levels", null);
        }
        if (value instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) value;

            if (compound.contains(NATIVE_ITEM_ID)) {
                // These current names also had different meanings before flattening. Restore only identities which
                // our recovery selected, leaving ordinary old vanilla items to their normal fixers.
                String target = compound.getString(NATIVE_ITEM_ID);
                if (!compound.contains(NATIVE_ITEM_ID, 8)
                        || !(target.equals("minecraft:grass") || target.equals("minecraft:stone_slab")
                                || target.equals("minecraft:stone_stairs"))
                        || !compound.contains("id", 8)
                        || !compound.contains("Count", 99)) {
                    throw new LegacyBridge.RecoveryAbort("Invalid pending native item recovery", null);
                }

                stacks.add(compound);
            }
            if (compound.contains(NumericStateBridge.NATIVE_STATE_ID)) {
                CompoundNBT properties = compound.getCompound("Properties");
                String half = properties.getString("type");
                String water = properties.getString("waterlogged");

                if (!compound.contains(NumericStateBridge.NATIVE_STATE_ID, 8)
                        || !compound.getString(NumericStateBridge.NATIVE_STATE_ID).equals("minecraft:stone_slab")
                        || !compound.contains("Name", 8) || !(half.equals("top") || half.equals("bottom"))
                        || !(water.equals("true") || water.equals("false"))) {
                    throw new LegacyBridge.RecoveryAbort("Invalid pending native stone slab recovery", null);
                }

                states.add(compound);
            }
            for (String key : compound.keySet()) {
                collectNativeTargets(compound.get(key), stacks, states, depth + 1);
            }
        } else if (value instanceof net.minecraft.nbt.ListNBT) {
            for (net.minecraft.nbt.INBT child : (net.minecraft.nbt.ListNBT) value) {
                collectNativeTargets(child, stacks, states, depth + 1);
            }
        }
    }

    static final class Registry {
        private final Map<String, Entry> entries = new LinkedHashMap<>();

        synchronized boolean hasContributors() {
            return !entries.isEmpty();
        }

        synchronized void register(String module, Predicate<CompoundNBT> relevant, Consumer<CompoundNBT> recovery) {
            if (module == null || !module.matches("[a-z][a-z0-9_]*") || relevant == null || recovery == null
                    || entries.containsKey(module)) {
                throw new IllegalArgumentException("Invalid or duplicate item recovery module: " + module);
            }

            entries.put(module, new Entry(module, relevant, recovery));
        }

        CompoundNBT prepareCopy(CompoundNBT root) {
            List<Entry> snapshot;

            synchronized (this) {
                if (entries.isEmpty()) {
                    return root;
                }

                snapshot = new ArrayList<>(entries.values());
            }

            List<Entry> matching = new ArrayList<>();

            for (Entry entry : snapshot) {
                try {
                    if (entry.relevant.test(root)) {
                        matching.add(entry);
                    }
                } catch (RuntimeException failure) {
                    throw blocked(entry, failure);
                }
            }
            if (matching.isEmpty()) {
                return root;
            }

            CompoundNBT result = root.copy();

            for (Entry entry : matching) {
                try {
                    entry.recovery.accept(result);
                } catch (RuntimeException failure) {
                    throw blocked(entry, failure);
                }
            }

            return result;
        }

        private LegacyBridge.RecoveryAbort blocked(Entry entry, RuntimeException failure) {
            return new LegacyBridge.RecoveryAbort("World upgrade stopped before item deserialization: " + entry.module
                    + ": " + failure.getMessage() + ". Restore the world backup and required mods.", failure);
        }
    }

    private static final class Entry {
        final String module;
        final Predicate<CompoundNBT> relevant;
        final Consumer<CompoundNBT> recovery;

        Entry(String module, Predicate<CompoundNBT> relevant, Consumer<CompoundNBT> recovery) {
            this.module = module;
            this.relevant = relevant;
            this.recovery = recovery;
        }
    }
}
