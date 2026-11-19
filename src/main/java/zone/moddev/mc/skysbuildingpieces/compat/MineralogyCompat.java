package zone.moddev.mc.skysbuildingpieces.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;

/** Optional rock pieces belong to the core; Mineralogy still supplies the rocks and textures. */
public final class MineralogyCompat {
    public static final String ASSETS = "skysbuildingpiecesmineralogy";
    public static final int BLOCK_COUNT = 35;
    private static final String ROOT = "/assets/" + ASSETS + "/catalogue/";
    private static final Set<String> FORMER_PATHS = formerPaths();

    private MineralogyCompat() {
    }

    public static void register() {
        if (ModList.isLoaded(ASSETS)) {
            throw new IllegalStateException("Mineralogy pieces are included in Sky's Building Pieces now. "
                    + "Remove the separate Sky's Building Pieces Mineralogy add-on jar.");
        }
        if (!ModList.isLoaded("mineralogy")) {
            return;
        }

        JsonArray materials = Catalogue.read(MineralogyCompat.class, ROOT + "materials.json");
        Catalogue.Module module = Catalogue.INSTANCE.registerModule("mineralogy", SkysBuildingPieces.ID, materials,
                palettes(), BLOCK_COUNT);

        if (module.materials.size() != 27 || module.palettes.size() != BLOCK_COUNT) {
            throw new IllegalStateException("Mineralogy pieces must use 27 materials and 35 fixed palettes");
        }
    }

    /** Prefix only the registry paths; material slots and orientations stay unchanged. */
    public static JsonArray palettes() {
        JsonArray palettes = Catalogue.read(MineralogyCompat.class, ROOT + "palettes.json");

        for (JsonElement entry : palettes) {
            JsonObject palette = entry.getAsJsonObject();

            palette.addProperty("id", "mineralogy_" + palette.get("id").getAsString());
        }

        return palettes;
    }

    public static String replacementId(String historicalId) {
        String prefix = ASSETS + ":";

        if (!historicalId.startsWith(prefix)) {
            return null;
        }

        String path = historicalId.substring(prefix.length());

        return FORMER_PATHS.contains(path) ? SkysBuildingPieces.ID + ":mineralogy_" + path : null;
    }

    /** Forge remapping retains metadata and item NBT for saves made with the unreleased add-on. */
    public static Block formerBlock(String historicalId) {
        String replacement = replacementId(historicalId);

        if (replacement == null) {
            return null;
        }

        Block target = ForgeRegistries.BLOCKS.getValue(Identifier.parse(replacement));

        if (target == null || target == Blocks.AIR) {
            throw new IllegalStateException("Cannot recover " + historicalId
                    + ". Restore Mineralogy before loading this world; its rock pieces are still saved.");
        }

        return target;
    }

    /** Older unopened inventories may outlive Forge's temporary registry aliases. */
    public static void prepareStacks(Tag value) {
        List<StackIdentity> changes = new ArrayList<StackIdentity>();

        collectStacks(value, changes);

        // Resolve every target first. A bad nested piece must not leave a half-converted inventory.
        for (StackIdentity change : changes) {
            change.stack.putString("id", change.replacement);
        }
    }

    private static void collectStacks(Tag value, List<StackIdentity> changes) {
        if (value instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) value;

            if (compound.getString("id").isPresent() && compound.getInt("Count").isPresent()) {
                String replacement = replacementId(compound.getStringOr("id", ""));

                if (replacement != null) {
                    Block target = formerBlock(compound.getStringOr("id", ""));
                    // Validate the saved slot without normalising its placement orientation.
                    int metadata = compound.getShortOr("Damage", (short) 0);

                    if (metadata < 0 || metadata > 15) {
                        throw new IllegalStateException("Invalid saved Mineralogy piece metadata: " + metadata);
                    }

                    ((PieceBlock) target).palette.material(metadata);
                    changes.add(new StackIdentity(compound, replacement));
                }
            }
            for (String key : compound.keySet()) {
                collectStacks(compound.get(key), changes);
            }
        } else if (value instanceof ListTag) {
            ListTag list = (ListTag) value;

            for (int index = 0; index < list.size(); index++) {
                collectStacks(list.get(index), changes);
            }
        }
    }

    private static final class StackIdentity {
        final CompoundTag stack;
        final String replacement;

        StackIdentity(CompoundTag stack, String replacement) {
            this.stack = stack;
            this.replacement = replacement;
        }
    }

    private static Set<String> formerPaths() {
        Set<String> paths = new LinkedHashSet<String>();

        for (JsonElement entry : Catalogue.read(MineralogyCompat.class, ROOT + "palettes.json")) {
            paths.add(entry.getAsJsonObject().get("id").getAsString());
        }

        return Collections.unmodifiableSet(paths);
    }

}
