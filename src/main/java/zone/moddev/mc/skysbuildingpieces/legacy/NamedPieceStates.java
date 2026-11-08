package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Recover already named palette states after vanilla has finished updating the save. */
final class NamedPieceStates {
    private NamedPieceStates() {
    }

    static void flatten(CompoundTag root) {
        List<Runnable> changes = new ArrayList<>();
        collect(root, false, changes);
        changes.forEach(Runnable::run);
    }

    private static Tag collect(Tag value, boolean blockStates, List<Runnable> changes) {
        if (blockStates) {
            // The 26.3 palette codec stores default states as names, sometimes wrapped in a mixed list.
            String compactName = value instanceof StringTag name ? name.value()
                    : value instanceof CompoundTag compound && compound.keySet().size() == 1
                            ? compound.getStringOr("", "") : "";
            if (!compactName.isEmpty()) {
                PieceBlock carrier = LegacyBridge.palette(compactName);
                if (carrier != null) {
                    return NbtUtils.writeBlockState(Pieces.canonicalState(carrier.defaultBlockState()));
                }
            }
        }
        if (value instanceof ListTag list) {
            for (int index = 0; index < list.size(); index++) {
                Tag entry = list.get(index);
                Tag target = collect(entry, blockStates, changes);
                if (target != entry) {
                    int slot = index;
                    changes.add(() -> list.set(slot, target));
                }
            }
        } else if (value instanceof CompoundTag compound) {
            String nameKey = nameKey(compound);
            PieceBlock carrier = blockStates ? LegacyBridge.palette(compound.getStringOr(nameKey, "")) : null;
            if (carrier != null) {
                BlockState state = resolve(carrier, compound);
                CompoundTag target = nameKey.equals("id") ? NbtUtils.writeBlockState(state)
                        : NumericStateBridge.writeSavedState(state);
                changes.add(() -> replaceNamedState(compound, target, nameKey));
                return value;
            }

            for (String key : compound.keySet()) {
                switch (key) {
                    case "tag", "components", "ForgeCaps", "neoforge:attachments" -> { }
                    default -> {
                        Tag entry = compound.get(key);
                        Tag target = collect(entry, isStateField(key), changes);
                        if (target != entry) {
                            changes.add(() -> compound.put(key, target));
                        }
                    }
                }
            }
        }

        return value;
    }

    private static boolean isStateField(String key) {
        return switch (key) {
            case "Palette", "palette", "palettes", "BlockState", "blockState", "block_state",
                    "carriedBlockState", "DisplayState" -> true;
            default -> false;
        };
    }

    private static void replaceNamedState(CompoundTag saved, CompoundTag target, String nameKey) {
        String propertiesKey = propertiesKey(saved);
        saved.putString(nameKey, target.getString(nameKey).orElseThrow());
        if (target.get(propertiesKey) instanceof CompoundTag properties) {
            saved.put(propertiesKey, properties);
        } else {
            saved.remove(propertiesKey);
        }
    }

    static BlockState resolve(PieceBlock carrier, CompoundTag saved) {
        try {
            String propertiesKey = propertiesKey(saved);
            CompoundTag properties = saved.getCompoundOrEmpty(propertiesKey);
            if (saved.contains(propertiesKey) && !(saved.get(propertiesKey) instanceof CompoundTag)) {
                throw new IllegalArgumentException("Properties must be a compound");
            }
            int metadata = properties.contains("meta") ? Integer.parseInt(properties.getStringOr("meta", "")) : 0;
            BlockState state = carrier.getStateFromMeta(metadata);
            for (String name : properties.keySet()) {
                if (!name.equals("meta")) {
                    Property<?> property = carrier.getStateDefinition().getProperty(name);
                    if (property == null) {
                        throw new IllegalArgumentException("Unknown block state property " + name);
                    }
                    state = withProperty(state, property, properties.getStringOr(name, ""));
                }
            }
            return Pieces.canonicalState(state);
        } catch (RuntimeException failure) {
            throw new LegacyBridge.RecoveryAbort("Cannot recover " + saved.getStringOr(nameKey(saved), "") + ": "
                    + failure.getMessage(), failure);
        }
    }

    private static String nameKey(CompoundTag state) {
        return state.contains("id") ? "id" : "Name";
    }

    private static String propertiesKey(CompoundTag state) {
        return state.contains("id") ? "properties" : "Properties";
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property,
            String value) {
        return state.setValue(property, property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid " + property.getName() + "=" + value)));
    }
}
