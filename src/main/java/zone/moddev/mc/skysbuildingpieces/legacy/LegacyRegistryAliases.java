package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/** Lets companion mods share a rename without replacing an existing mapping. */
public final class LegacyRegistryAliases {
    private LegacyRegistryAliases() {
    }

    public static void register(Registry<?> registry, Identifier former, Identifier target) {
        Identifier expected = registry.resolve(target);
        Identifier current = registry.resolve(former);
        if (current.equals(expected)) {
            return;
        }
        if (!current.equals(former) || registry.containsKey(former)) {
            throw new IllegalStateException("Cannot recover " + former + " as " + target
                    + " in " + registry.key().identifier() + "; it already resolves to " + current);
        }
        registry.addAlias(former, expected);
    }
}
