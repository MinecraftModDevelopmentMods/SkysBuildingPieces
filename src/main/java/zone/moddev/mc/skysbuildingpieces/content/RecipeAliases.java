package zone.moddev.mc.skysbuildingpieces.content;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.event.world.WorldEvent;

/** Old recipe books resolve to the native recipe without registering a second craft. */
@Mod.EventBusSubscriber(modid = "skysbuildingpieces")
public final class RecipeAliases {
    private static final Map<ResourceLocation, ResourceLocation> REDIRECTS = new LinkedHashMap<>();
    private RecipeAliases() {
    }

    static void redirect(IForgeRegistry<IRecipe> registry, ResourceLocation previous, ResourceLocation current) {
        ResourceLocation existing = REDIRECTS.putIfAbsent(previous, current);
        if (existing != null && !existing.equals(current)) {
            throw new IllegalStateException("Conflicting slab recipe alias " + previous);
        }
        apply(registry, previous, current);
    }

    /** Registry snapshots replace the alias table when a saved world is opened. */
    public static void restore() {
        if (REDIRECTS.isEmpty()) {
            return;
        }
        IForgeRegistry<IRecipe> registry = GameRegistry.findRegistry(IRecipe.class);
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : REDIRECTS.entrySet()) {
            apply(registry, entry.getKey(), entry.getValue());
        }
    }

    @SubscribeEvent
    public static void worldLoaded(WorldEvent.Load event) {
        restore();
    }

    private static void apply(IForgeRegistry<IRecipe> registry, ResourceLocation previous, ResourceLocation current) {
        if (registry.getValue(previous) == registry.getValue(current) && registry.getValue(current) != null) {
            return;
        }
        if (!(registry instanceof ForgeRegistry) || registry.containsKey(previous)
                || registry.getValue(current) == null || previous.equals(current)) {
            throw new IllegalStateException("Cannot redirect the old slab recipe " + previous + " to " + current);
        }
        ForgeRegistry<IRecipe> forge = (ForgeRegistry<IRecipe>) registry;
        boolean locked = forge.isLocked();
        try {
            // Forge locks this table before its remap event. Restore our names, then restore the lock.
            if (locked) {
                forge.unfreeze();
            }
            AliasMethod.ADD.invoke(registry, previous, current);
            if (registry.getValue(previous) != registry.getValue(current)) {
                throw new IllegalStateException("Forge did not retain the slab recipe alias " + previous);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot preserve the old slab recipe " + previous, failure);
        } finally {
            if (locked) {
                forge.freeze();
            }
        }
    }

    private static final class AliasMethod {
        static final Method ADD = find();

        private static Method find() {
            try {
                // Forge 1.12 keeps this method package-private; no registry fields are changed directly.
                Method method = ForgeRegistry.class.getDeclaredMethod("addAlias", ResourceLocation.class,
                        ResourceLocation.class);
                if (method.getReturnType() != void.class) {
                    throw new IllegalStateException("Unexpected Forge recipe alias signature");
                }
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Forge's recipe alias method is unavailable", failure);
            }
        }
    }
}
