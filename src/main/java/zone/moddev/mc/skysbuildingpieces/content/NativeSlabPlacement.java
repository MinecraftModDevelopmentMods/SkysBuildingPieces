package zone.moddev.mc.skysbuildingpieces.content;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Exact catalogue slab items use the same face-local placement as our own items. */
public final class NativeSlabPlacement {
    private static NativeSlabPlacement instance;
    private final Map<Item, String> materials = new HashMap<>();

    private NativeSlabPlacement() {
        for (Catalogue.MaterialDef definition : Catalogue.INSTANCE.materials.values()) {
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
    public static ActionResultType place(ItemUseContext context) {
        if (context.getPlayer() == null) {
            return null;
        }

        BlockPos position = context.getPos();
        Vec3d hit = context.getHitVec();

        return place(context.getItem(), context.getPlayer(), context.getWorld(), position, context.getHand(),
                context.getFace(), (float) (hit.x - position.getX()), (float) (hit.y - position.getY()),
                (float) (hit.z - position.getZ()));
    }

    /** Null leaves the original item-use path intact. */
    public static ActionResultType place(PlayerEntity player, World world, BlockPos position, Hand hand, Direction face,
            float hitX, float hitY, float hitZ) {
        return place(player.getHeldItem(hand), player, world, position, hand, face, hitX, hitY, hitZ);
    }

    public static ActionResultType place(ItemStack stack, PlayerEntity player, World world, BlockPos position,
            Hand hand, Direction face, float hitX, float hitY, float hitZ) {
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
