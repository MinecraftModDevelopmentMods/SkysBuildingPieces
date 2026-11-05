package zone.moddev.mc.skysbuildingpieces.content;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Exact catalogue slab items use the same face-local placement as our own items. */
public final class NativeSlabPlacement {
    private static NativeSlabPlacement instance;
    private final Map<Item, String> materials = new HashMap<>();

    private NativeSlabPlacement() {
        for (Catalogue.MaterialDef definition : Catalogue.INSTANCE.materials.values()) {
            if (definition.replacement() != null) {
                continue;
            }
            if (Pieces.state(definition.id, Shape.VERTICAL_SLAB, 0) == null) {
                continue;
            }

            BlockState slab = Pieces.nativeState(definition, "slab");

            if (slab == null || NativeSlabState.mask(slab) < 0) {
                continue;
            }

            ItemStack stack = Pieces.stack(slab, 1);

            if (stack == null) {
                continue;
            }

            String previous = materials.put(stack.getItem(), definition.id);

            if (previous != null && !previous.equals(definition.id)) {
                throw new IllegalStateException(
                        "Conflicting native slab materials: " + previous + " and " + definition.id);
            }
        }
    }

    public static void initialize() {
        instance = new NativeSlabPlacement();
    }

    public String material(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : materials.get(stack.getItem());
    }

    /** The BlockItem hook supplies the exact hit position from the item-use packet. */
    public static InteractionResult place(UseOnContext context) {
        if (context.getPlayer() == null) {
            return null;
        }

        BlockPos position = context.getClickedPos();
        Vec3 hit = context.getClickLocation();

        return place(context.getItemInHand(), context.getPlayer(), context.getLevel(), position, context.getHand(),
                context.getClickedFace(), (float) (hit.x - position.getX()), (float) (hit.y - position.getY()),
                (float) (hit.z - position.getZ()));
    }

    /** Null leaves the original item-use path intact. */
    public static InteractionResult place(Player player, Level world, BlockPos position, InteractionHand hand, Direction face,
            float hitX, float hitY, float hitZ) {
        return place(player.getItemInHand(hand), player, world, position, hand, face, hitX, hitY, hitZ);
    }

    public static InteractionResult place(ItemStack stack, Player player, Level world, BlockPos position,
            InteractionHand hand, Direction face, float hitX, float hitY, float hitZ) {
        if (instance == null || ModList.get().isLoaded("buildingbricks")) {
            return null;
        }

        String material = instance.material(stack);

        if ("minecraft:dirt".equals(material)
                && SlabPlacement.side(face, hitX, hitY, hitZ).getAxis() == Direction.Axis.Y) {
            return null;
        }

        return material == null ? null
                : PieceItem.place(stack, player, world, position, hand, face, hitX, hitY, hitZ, material, Shape.SLAB);
    }
}
