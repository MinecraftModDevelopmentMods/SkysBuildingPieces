package zone.moddev.mc.skysbuildingpieces.content;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

/** The book accepts the same saved orientations as the recombination recipe. */
final class PieceIngredients {
    static Ingredient fromState(IBlockState state) {
        ItemStack canonical = Pieces.stack(state, 1);
        Block block = state.getBlock();
        List<ItemStack> variants = new ArrayList<>();
        variants.add(canonical.copy());
        for (int metadata = 0; metadata < 16; metadata++) {
            if (metadata == canonical.getMetadata()) {
                continue;
            }
            boolean matching = false;
            if (block instanceof PieceBlock) {
                try {
                    matching = ((PieceBlock) block).canonicalMeta(block.getStateFromMeta(metadata))
                            == canonical.getMetadata();
                } catch (IllegalArgumentException unusedSlot) {
                    continue;
                }
            } else if (block instanceof BlockSlab) {
                matching = (metadata & 7) == canonical.getMetadata();
            }
            if (matching) {
                ItemStack variant = canonical.copy();
                variant.setItemDamage(metadata);
                variants.add(variant);
            }
        }
        // Keep saved orientations in the matching and auto-fill lists, but show the normal item.
        return new Ingredient(variants.toArray(new ItemStack[variants.size()])) {
            private final ItemStack[] preview = {canonical.copy()};

            @Override
            public ItemStack[] getMatchingStacks() {
                return preview;
            }
        };
    }

    private PieceIngredients() {
    }
}
