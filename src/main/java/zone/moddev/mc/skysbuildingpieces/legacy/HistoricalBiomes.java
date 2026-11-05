package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;

/** Internal bridge for biome identities stored before section biome palettes. */
public final class HistoricalBiomes {
    private static final String PENDING = "skysbuildingpieces_biomes";
    private static final String PENDING_NAMES = "skysbuildingpieces_biome_names";
    private static final Map<String, Map<String, String>> MODULES = new LinkedHashMap<>();
    private static volatile Mappings active = new Mappings(Collections.emptyMap(), Collections.emptyMap(), name -> -1);

    private HistoricalBiomes() {
    }

    public static synchronized void register(String namespace, Map<String, String> aliases) {
        if (LegacyBridge.ready || namespace == null || !namespace.matches("[a-z][a-z0-9_]*") || aliases == null
                || MODULES.containsKey(namespace)) {
            throw new IllegalArgumentException("Invalid or late historical biome module: " + namespace);
        }

        Map<String, String> checked = new LinkedHashMap<>();

        aliases.forEach((source, target) -> {
            if (!source.startsWith(namespace + ":") || target == null) {
                throw new IllegalArgumentException("Invalid historical biome alias: " + source);
            }

            checked.put(ResourceLocation.parse(source).toString(), ResourceLocation.parse(target).toString());
        });
        MODULES.put(namespace, Collections.unmodifiableMap(checked));
    }

    static synchronized void begin(CompoundTag snapshot) {
        Map<Integer, String> source = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();

        MODULES.values().forEach(aliases::putAll);

        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        ListTag entries = snapshot.getCompound("Registries").getCompound("minecraft:biomes").getList("ids", 10);

        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);

            if (!entry.contains("K", 8) || !entry.contains("V", 99)) {
                throw blocked("invalid source biome registration: " + entry);
            }

            String name = entry.getString("K");
            ResourceLocation identity;

            try {
                identity = ResourceLocation.parse(name);
            } catch (RuntimeException malformed) {
                throw blocked("invalid source biome name: " + name);
            }

            long savedNumber = entry.getLong("V");

            if (savedNumber < 0 || savedNumber > Integer.MAX_VALUE || !numbers.add((int) savedNumber)
                    || !names.add(name)) {
                throw blocked("conflicting source biome registration: " + entry);
            }
            if (!MODULES.containsKey(identity.getNamespace())) {
                continue;
            }

            int numeric = (int) savedNumber;

            if (numeric < 0 || numeric > 255 || source.putIfAbsent(numeric, name) != null) {
                throw blocked("invalid source biome registration: " + entry);
            }
        }

        active = new Mappings(source, aliases, name -> {
            // Numeric IDs belong to the world's biome instances, which can differ from Forge's registration objects.
            Registry<Biome> current = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                    .registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            Biome biome = current.get(ResourceLocation.parse(name));

            return biome == null ? -1 : current.getId(biome);
        });
    }

    static void validate(CompoundTag root) {
        translate(root);
    }

    static boolean prepare(CompoundTag root) {
        int[] biomes = translate(root);

        if (biomes == null) {
            return false;
        }

        CompoundTag level = root.getCompound("Level");
        byte[] saved = level.getByteArray("Biomes");
        ListTag names = new ListTag();
        for (byte value : saved) {
            String source = active.source.get(value & 255);
            names.add(StringTag.valueOf(source == null ? "" : active.aliases.getOrDefault(source, source)));
        }
        // Keep identities as well as numbers: 1.18's native fixer only knows the
        // vanilla numeric table when it builds the section biome palettes.
        level.putIntArray(PENDING, biomes);
        level.put(PENDING_NAMES, names);
        return true;
    }

    /**
     * Keep an add-on's resolved names through the 1.18 biome fixer. Numeric IDs
     * alone are unsafe here: vanilla interprets them using its own old table.
     * The caller supplies the original cells before translating their numbers.
     */
    public static void preserveNamed(CompoundTag root, int[] source, Map<Integer, String> replacements) {
        CompoundTag level = root.getCompound("Level");
        int[] translated = level.getIntArray("Biomes");
        if ((source.length != 256 && source.length != 1024) || translated.length != source.length) {
            throw blocked("invalid named biome recovery array");
        }
        ListTag names = new ListTag();
        for (int number : source) {
            String replacement = replacements.get(number);
            names.add(StringTag.valueOf(replacement == null ? "" : ResourceLocation.parse(replacement).toString()));
        }
        if (level.contains(PENDING) || level.contains(PENDING_NAMES)) {
            if (!level.contains(PENDING, 11) || !level.contains(PENDING_NAMES, 9)
                    || !level.get(PENDING).equals(new net.minecraft.nbt.IntArrayTag(translated))
                    || !names.equals(level.get(PENDING_NAMES))) {
                throw blocked("conflicting pending named biome recovery");
            }
            return;
        }
        level.putIntArray(PENDING, translated.clone());
        level.put(PENDING_NAMES, names);
    }

    private static int[] translate(CompoundTag root) {
        if (active.source.isEmpty() || root.getInt("DataVersion") > 1343 || !root.contains("Level", 10)) {
            return null;
        }

        CompoundTag level = root.getCompound("Level");

        if (!level.contains("Biomes")) {
            return null;
        }
        if (!level.contains("Biomes", 7) || level.getByteArray("Biomes").length != 256) {
            throw blocked("unsupported pre-flattening biome array in chunk " + level.getInt("xPos") + ", "
                    + level.getInt("zPos"));
        }

        return active.translate(level.getByteArray("Biomes"));
    }

    static void finish(CompoundTag root) {
        CompoundTag level = MigrationEvents.chunkLevel(root);

        if (!level.contains(PENDING)) {
            if (level.contains(PENDING_NAMES)) {
                throw blocked("incomplete pending biome recovery");
            }
            return;
        }
        int cells = level.getIntArray(PENDING).length;
        if (!level.contains(PENDING, 11) || (cells != 256 && cells != 1024)
                || level.getList(PENDING_NAMES, 8).size() != cells) {
            throw blocked("invalid pending biome recovery");
        }

        if (root.contains("Level", 10)) {
            // Retain the intermediate format for callers completing an older
            // fixing stage. Ordinary 1.18 chunk loads use the section path below.
            level.putIntArray("Biomes", level.getIntArray(PENDING));
        } else {
            restoreSectionBiomes(level);
        }
        level.remove(PENDING);
        level.remove(PENDING_NAMES);
    }

    private static void restoreSectionBiomes(CompoundTag chunk) {
        ListTag names = chunk.getList(PENDING_NAMES, 8);
        ListTag sections = chunk.getList("sections", 10);
        if ((names.size() != 256 && names.size() != 1024) || sections.isEmpty()) {
            throw blocked("missing names or sections for pending biome recovery");
        }

        java.util.List<Runnable> changes = new java.util.ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            CompoundTag nativeBiomes = section.getCompound("biomes");
            ListTag palette = nativeBiomes.getList("palette", 8);
            int bits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
            if (palette.isEmpty() || palette.size() > 64) {
                throw blocked("invalid native biome palette in section " + section.getByte("Y"));
            }
            int perLong = bits == 0 ? 64 : 64 / bits;
            long[] packed = nativeBiomes.getLongArray("data");
            if (bits > 0 && packed.length != (64 + perLong - 1) / perLong) {
                throw blocked("invalid native biome storage in section " + section.getByte("Y"));
            }

            Map<String, Integer> indices = new LinkedHashMap<>();
            int[] cells = new int[64];
            for (int cell = 0; cell < cells.length; cell++) {
                int oldIndex = bits == 0 ? 0
                        : (int) (packed[cell / perLong] >>> (cell % perLong * bits) & ((1L << bits) - 1));
                if (oldIndex >= palette.size()) {
                    throw blocked("out-of-range native biome palette entry");
                }
                String replacement;
                if (names.size() == 256) {
                    // Match vanilla's old two-dimensional-to-quart sampling.
                    int column = (cell & 3) * 4 + 2;
                    int row = (cell >> 2 & 3) * 4 + 2;
                    replacement = names.getString(row * 16 + column);
                } else {
                    // The 1.15–1.17 format already stores a 4×4×64 volume.
                    // Vanilla extends its bottom and top planes into new height.
                    int height = Math.max(0, Math.min(63, section.getByte("Y") * 4 + (cell >> 4)));
                    replacement = names.getString(height * 16 + (cell & 15));
                }
                String name = replacement.isEmpty() ? palette.getString(oldIndex) : replacement;
                cells[cell] = indices.computeIfAbsent(name, ignored -> indices.size());
            }

            CompoundTag restored = nativeBiomes.copy();
            ListTag restoredPalette = new ListTag();
            indices.keySet().forEach(name -> restoredPalette.add(StringTag.valueOf(name)));
            restored.put("palette", restoredPalette);
            int restoredBits = 32 - Integer.numberOfLeadingZeros(indices.size() - 1);
            if (restoredBits == 0) {
                restored.remove("data");
            } else {
                int restoredPerLong = 64 / restoredBits;
                long[] restoredData = new long[(64 + restoredPerLong - 1) / restoredPerLong];
                for (int cell = 0; cell < cells.length; cell++) {
                    restoredData[cell / restoredPerLong] |= (long) cells[cell]
                            << (cell % restoredPerLong * restoredBits);
                }
                restored.putLongArray("data", restoredData);
            }
            changes.add(() -> section.put("biomes", restored));
        }

        // Validate every section before replacing any of them.
        changes.forEach(Runnable::run);
    }

    static void clear() {
        active = new Mappings(Collections.emptyMap(), Collections.emptyMap(), name -> -1);
    }

    private static LegacyBridge.RecoveryAbort blocked(String detail) {
        return new LegacyBridge.RecoveryAbort("World upgrade stopped: " + detail
                + ". Restore the original world and required biome add-on before retrying.", null);
    }

    static final class Mappings {
        final Map<Integer, String> source;
        final Map<String, String> aliases;
        final ToIntFunction<String> target;
        private final Map<Integer, Integer> resolved = new java.util.concurrent.ConcurrentHashMap<>();

        Mappings(Map<Integer, String> source, Map<String, String> aliases, ToIntFunction<String> target) {
            this.source = Collections.unmodifiableMap(new LinkedHashMap<>(source));
            this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
            this.target = target;
        }

        int[] translate(byte[] saved) {
            int[] result = new int[saved.length];
            boolean changed = false;

            for (int column = 0; column < saved.length; column++) {
                int numeric = saved[column] & 255;
                String name = source.get(numeric);

                result[column] = numeric;

                if (name != null) {
                    String replacement = aliases.getOrDefault(name, name);
                    int current = resolved.computeIfAbsent(numeric, ignored -> target.applyAsInt(replacement));

                    if (current < 0) {
                        throw blocked("no biome replacement for " + name + " (expected " + replacement + ")");
                    }

                    result[column] = current;
                    changed = true;
                }
            }

            return changed ? result : null;
        }
    }
}
