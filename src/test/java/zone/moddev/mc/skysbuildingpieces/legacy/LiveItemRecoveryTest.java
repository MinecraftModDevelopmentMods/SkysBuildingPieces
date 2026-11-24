package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap;

class LiveItemRecoveryTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 64, 99, 100, 1895, Integer.MAX_VALUE})
    void unrelatedStorageStacksKeepTheirIdentityAndFullCount(int count) {
        prepareStone();
        ItemStack original = new ItemStack(Items.STONE, count);

        assertSame(original, LiveItemRecovery.convert(original, registries()));
        assertEquals(count, original.getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 99, 100, 1895, Integer.MAX_VALUE})
    void namesAndForeignCustomDataDoNotMakeLargeStacksInvalid(int count) {
        prepareStone();
        ItemStack original = new ItemStack(Items.STONE, count);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Stored stone"));
        CompoundTag custom = new CompoundTag();
        custom.putString("owner", "A storage mod");
        custom.putInt("storedCount", 50000);
        original.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        ItemStack before = original.copy();

        assertSame(original, LiveItemRecovery.convert(original, registries()));
        assertTrue(ItemStack.matches(before, original));
        assertEquals(custom, original.get(DataComponents.CUSTOM_DATA).copyTag());
    }

    private static HolderLookup.Provider registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static void prepareStone() {
        MinecraftTestBootstrap.registerVanilla();
        // Plain JUnit has no datapack component reload. The native server checks
        // use the complete item defaults; this test needs stone's stack limit.
        if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) {
            Items.STONE.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE, 64).build());
        }
    }
}
