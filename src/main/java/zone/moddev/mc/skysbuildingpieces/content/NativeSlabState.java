package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/** Native slabs may use either vanilla's half bit or a six-direction facing property. */
final class NativeSlabState {
    private NativeSlabState() {
    }

    static EnumFacing facingFor(int orientation) {
        return orientation == 0 ? EnumFacing.DOWN : EnumFacing.UP;
    }

    static int vanillaMetadata(int metadata, int orientation) {
        return (metadata & ~8) | (orientation == 0 ? 8 : 0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static IBlockState orient(IBlockState nativeState, int orientation) {
        for (IProperty property : nativeState.getPropertyKeys()) {
            if (property.getName().equals("facing")
                    && property.getAllowedValues().contains(EnumFacing.UP)
                    && property.getAllowedValues().contains(EnumFacing.DOWN)) {
                return nativeState.withProperty(property, facingFor(orientation));
            }
        }
        return nativeState.getBlock().getStateFromMeta(vanillaMetadata(
                nativeState.getBlock().getMetaFromState(nativeState), orientation));
    }
}
