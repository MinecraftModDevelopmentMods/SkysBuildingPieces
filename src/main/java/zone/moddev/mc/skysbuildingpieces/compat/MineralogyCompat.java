package zone.moddev.mc.skysbuildingpieces.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
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
        if (Loader.isModLoaded(ASSETS)) {
            throw new IllegalStateException("Mineralogy pieces are included in Sky's Building Pieces now. " +
                    "Remove the separate Sky's Building Pieces Mineralogy add-on jar.");
        }
        if (!Loader.isModLoaded("mineralogy")) {
            return;
        }

        JsonArray materials = Catalogue.read(MineralogyCompat.class, ROOT + "materials.json");
        if (!nativeSlabsEnabled()) {
            // Disabled native slabs must not leave recipes pointing at missing blocks.
            for (JsonElement material : materials) {
                material.getAsJsonObject().getAsJsonObject("blocks").remove("slab");
            }
        }
        Catalogue.Module module = Catalogue.INSTANCE.registerModule("mineralogy", SkysBuildingPieces.ID,
                materials, palettes(), BLOCK_COUNT);
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
        Block target = Block.REGISTRY.getObject(new ResourceLocation(replacement));
        if (target == null || target == Blocks.AIR) {
            throw new IllegalStateException("Cannot recover " + historicalId +
                    ". Restore Mineralogy before loading this world; its rock pieces are still saved.");
        }
        return target;
    }

    /** Older unopened inventories may outlive Forge's temporary registry aliases. */
    public static void prepareStacks(NBTBase value) {
        List<StackIdentity> changes = new ArrayList<StackIdentity>();
        collectStacks(value, changes);
        // Resolve every target first. A bad nested piece must not leave a half-converted inventory.
        for (StackIdentity change : changes) {
            change.stack.setString("id", change.replacement);
        }
    }

    private static void collectStacks(NBTBase value, List<StackIdentity> changes) {
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) value;
            if (compound.hasKey("id", 8) && compound.hasKey("Count", 99)) {
                String replacement = replacementId(compound.getString("id"));
                if (replacement != null) {
                    Block target = formerBlock(compound.getString("id"));
                    // Validate the saved slot without normalising its placement orientation.
                    int metadata = compound.getShort("Damage");
                    if (metadata < 0 || metadata > 15) {
                        throw new IllegalStateException("Invalid saved Mineralogy piece metadata: " + metadata);
                    }
                    ((PieceBlock) target).palette.material(metadata);
                    changes.add(new StackIdentity(compound, replacement));
                }
            }
            for (String key : compound.getKeySet()) {
                collectStacks(compound.getTag(key), changes);
            }
        } else if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;
            for (int index = 0; index < list.tagCount(); index++) {
                collectStacks(list.get(index), changes);
            }
        }
    }

    private static final class StackIdentity {
        final NBTTagCompound stack;
        final String replacement;

        StackIdentity(NBTTagCompound stack, String replacement) {
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

    private static boolean nativeSlabsEnabled() {
        try {
            Class<?> configuration = Class.forName("zone.moddev.mc.mineralogy.MineralogyConfig");
            return (Boolean) configuration.getMethod("generateRockSlab").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read Mineralogy's slab setting; check its installed version.", failure);
        }
    }
}
