package zone.moddev.mc.skysbuildingpieces.mixin;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zone.moddev.mc.skysbuildingpieces.legacy.SparseBiomeIds;

/** A biome palette needs room for saved registry IDs even if some numbers are unused. */
@Mixin(Strategy.class)
abstract class SparseBiomePaletteMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/IdMap;size()I"))
    private int skysbuildingpieces$biomeIdWidth(IdMap<?> ids, IdMap<?> original, int bitsPerAxis) {
        // Biome sections are 4 by 4 by 4. Block state sections keep vanilla's width.
        return bitsPerAxis == 2 ? SparseBiomeIds.storageSize(ids) : ids.size();
    }
}
