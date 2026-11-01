package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;

/** Internal bridge for biome numbers stored in pre-flattening chunks. */
public final class HistoricalBiomes {
    private static final String PENDING = "skysbuildingpieces_biomes";
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

            checked.put(new ResourceLocation(source).toString(), new ResourceLocation(target).toString());
        });
        MODULES.put(namespace, Collections.unmodifiableMap(checked));
    }

    static synchronized void begin(CompoundNBT snapshot) {
        Map<Integer, String> source = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();

        MODULES.values().forEach(aliases::putAll);

        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        ListNBT entries = snapshot.getCompound("Registries").getCompound("minecraft:biomes").getList("ids", 10);

        for (int index = 0; index < entries.size(); index++) {
            CompoundNBT entry = entries.getCompound(index);

            if (!entry.contains("K", 8) || !entry.contains("V", 99)) {
                throw blocked("invalid source biome registration: " + entry);
            }

            String name = entry.getString("K");
            ResourceLocation identity;

            try {
                identity = new ResourceLocation(name);
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
            Registry<Biome> current = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer()
                    .registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
            Biome biome = current.get(new ResourceLocation(name));

            return biome == null ? -1 : current.getId(biome);
        });
    }

    static void validate(CompoundNBT root) {
        translate(root);
    }

    static boolean prepare(CompoundNBT root) {
        int[] biomes = translate(root);

        if (biomes == null) {
            return false;
        }

        root.getCompound("Level").putIntArray(PENDING, biomes);
        return true;
    }

    private static int[] translate(CompoundNBT root) {
        if (active.source.isEmpty() || root.getInt("DataVersion") > 1343 || !root.contains("Level", 10)) {
            return null;
        }

        CompoundNBT level = root.getCompound("Level");

        if (!level.contains("Biomes")) {
            return null;
        }
        if (!level.contains("Biomes", 7) || level.getByteArray("Biomes").length != 256) {
            throw blocked("unsupported pre-flattening biome array in chunk " + level.getInt("xPos") + ", "
                    + level.getInt("zPos"));
        }

        return active.translate(level.getByteArray("Biomes"));
    }

    static void finish(CompoundNBT root) {
        CompoundNBT level = root.getCompound("Level");

        if (!level.contains(PENDING)) {
            return;
        }
        if (!level.contains(PENDING, 11) || level.getIntArray(PENDING).length != 256) {
            throw blocked("invalid pending biome recovery");
        }

        // Vanilla's biome fixer recognises only vanilla's old numbers. Restore the selected mod biomes afterwards,
        // using today's full integer IDs.
        level.putIntArray("Biomes", level.getIntArray(PENDING));
        level.remove(PENDING);
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
