package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.Quotient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class CookingFuelResourcesTest {
    private static HolderLookup.Provider lookup;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.registerVanilla();
        lookup = VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
    }

    @ParameterizedTest
    @CsvSource({"slab,2", "step,4", "corner,8"})
    void nativeCodecRetainsThePlankFuelReference(String shape, int divisor) throws Exception {
        String path = "/data/skysbuildingpieces/context_int_provider/cooking/wood_" + shape + ".json";
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "Missing wooden fuel resource: " + path);
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            var ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
            var provider = ContextIntProviders.DIRECT_CODEC.parse(ops, json).getOrThrow();
            var quotient = assertInstanceOf(Quotient.class, provider);
            assertEquals(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS, quotient.left().unwrapKey().orElseThrow());
            assertEquals(divisor, quotient.right().value().getInt(null));
        }
    }
}
