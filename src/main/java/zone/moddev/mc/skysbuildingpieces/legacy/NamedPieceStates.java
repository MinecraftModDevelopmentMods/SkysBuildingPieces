package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
        List<Change> changes = new ArrayList<>();
        collect(root, false, changes);
        for (Change change : changes) {
            change.saved.putString("Name", change.target.getString("Name"));
            if (change.target.contains("Properties", Tag.TAG_COMPOUND)) {
                change.saved.put("Properties", change.target.getCompound("Properties"));
            } else {
                change.saved.remove("Properties");
            }
        }
    }

    private record Change(CompoundTag saved, CompoundTag target) {
    }

    private static void collect(Tag value, boolean blockStates, List<Change> changes) {
        if (value instanceof ListTag list) {
            for (Tag entry : list) {
                collect(entry, blockStates, changes);
            }
        } else if (value instanceof CompoundTag compound) {
            PieceBlock carrier = blockStates ? LegacyBridge.palette(compound.getString("Name")) : null;
            if (carrier != null) {
                changes.add(new Change(compound, NbtUtils.writeBlockState(resolve(carrier, compound))));
                return;
            }
            for (String key : compound.getAllKeys()) {
                switch (key) {
                    case "tag", "components", "ForgeCaps", "neoforge:attachments" -> { }
                    case "Palette", "palette", "palettes", "BlockState", "blockState", "block_state",
                            "carriedBlockState", "DisplayState" ->
                        collect(compound.get(key), true, changes);
                    default -> collect(compound.get(key), false, changes);
                }
            }
        }
    }

    static BlockState resolve(PieceBlock carrier, CompoundTag saved) {
        try {
            CompoundTag properties = saved.getCompound("Properties");
            if (saved.contains("Properties") && !saved.contains("Properties", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Properties must be a compound");
            }
            int metadata = properties.contains("meta") ? Integer.parseInt(properties.getString("meta")) : 0;
            BlockState state = carrier.getStateFromMeta(metadata);
            for (String name : properties.getAllKeys()) {
                if (!name.equals("meta")) {
                    Property<?> property = carrier.getStateDefinition().getProperty(name);
                    if (property == null) {
                        throw new IllegalArgumentException("Unknown block state property " + name);
                    }
                    state = withProperty(state, property, properties.getString(name));
                }
            }
            return Pieces.canonicalState(state);
        } catch (RuntimeException failure) {
            throw new LegacyBridge.RecoveryAbort("Cannot recover " + saved.getString("Name") + ": "
                    + failure.getMessage(), failure);
        }
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property,
            String value) {
        return state.setValue(property, property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid " + property.getName() + "=" + value)));
    }
}
