package zone.moddev.mc.skysbuildingpieces.mixin;

import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.fixes.BlockStateData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Typed access to the pre-flattening state table expanded by the companion mixin. */
@Mixin(BlockStateData.class)
public interface BlockStateDataAccessor {
    @Accessor("MAP")
    static Dynamic<?>[] skysbuildingpieces$getLegacyStateMap() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
