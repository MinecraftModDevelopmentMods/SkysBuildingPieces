package zone.moddev.mc.skysbuildingpieces;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyRegistryAliases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyRegistryAliasesTest {
    @Test
    void repeatsTheSameRenameWithoutReplacingAnotherEntry() {
        MinecraftTestBootstrap.registerVanilla();
        var key = ResourceKey.<String>createRegistryKey(ResourceLocation.fromNamespaceAndPath("test", "pieces"));
        var registry = new MappedRegistry<>(key, Lifecycle.stable());
        var former = ResourceLocation.fromNamespaceAndPath("test", "former");
        var replacement = ResourceLocation.fromNamespaceAndPath("test", "replacement");
        var other = ResourceLocation.fromNamespaceAndPath("test", "other");
        Registry.register(registry, replacement, "replacement");
        Registry.register(registry, other, "other");

        LegacyRegistryAliases.register(registry, former, replacement);
        LegacyRegistryAliases.register(registry, former, replacement);

        assertEquals(replacement, registry.resolve(former));
        assertThrows(IllegalStateException.class,
                () -> LegacyRegistryAliases.register(registry, former, other));
        assertThrows(IllegalStateException.class,
                () -> LegacyRegistryAliases.register(registry, other, replacement));
        assertEquals("other", registry.get(other));
    }
}
