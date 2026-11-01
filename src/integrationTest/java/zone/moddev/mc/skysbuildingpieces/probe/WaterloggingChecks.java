package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Random;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.content.PathPieces;

/** Uses the target's fluid-container contract for every assigned piece state. */
public final class WaterloggingChecks {
    private WaterloggingChecks() {
    }

    public static void run(ServerLevel world) {
        BlockPos position = new BlockPos(1158, 180, 1158);

        world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
        world.removeBlock(position.above(), false);

        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        int tested = 0;
        int decays = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            int assigned = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < assigned; metadata++) {
                BlockState dry = block.getStateFromMeta(metadata);
                String material = block.definition(dry).id;

                world.setBlock(position, dry, 18);
                require(block.canPlaceLiquid(world, position, dry, Fluids.WATER), "Dry piece accepts water");
                require(!block.canPlaceLiquid(world, position, dry, Fluids.LAVA), "Pieces reject lava waterlogging");
                require(block.placeLiquid(world, position, dry, Fluids.WATER.getSource(false)),
                        "Water enters the native fluid container");

                BlockState wet = world.getBlockState(position);
                BlockState wetDirt = material.equals("minecraft:grass") || material.equals("minecraft:grass_path")
                        ? Pieces.state("minecraft:dirt", block.palette.shape, block.orientation(dry))
                        : null;
                boolean preserved = wet.getBlock() == block && block.getMetaFromState(wet) == metadata;
                boolean decayed = wetDirt != null && wet.getBlock() == wetDirt.getBlock()
                        && ((PieceBlock) wet.getBlock()).mask(wet) == ((PieceBlock) wetDirt.getBlock()).mask(wetDirt);

                require((preserved || decayed) && wet.getValue(SlabBlock.WATERLOGGED)
                        && wet.getFluidState().getType() == Fluids.WATER,
                        "Waterlogging preserves geometry, allowing wet soil decay: " + dry + " became " + wet);
                require(!block.canPlaceLiquid(world, position, wet, Fluids.WATER)
                        && !block.placeLiquid(world, position, wet, Fluids.WATER.getSource(false)),
                        "An already wet piece rejects another water placement");
                require(world.getLiquidTicks().hasScheduledTick(position, Fluids.WATER),
                        "Waterlogging schedules the native fluid tick");
                require(block.pickupBlock(world, position, wet).is(Items.WATER_BUCKET)
                        && !world.getBlockState(position).getValue(SlabBlock.WATERLOGGED),
                        "Bucket pickup leaves the same dry piece");
                require(block.pickupBlock(world, position, world.getBlockState(position)).isEmpty(),
                        "Water cannot be collected twice");

                if (material.equals("minecraft:grass") || material.equals("minecraft:grass_path")) {
                    world.setBlock(position, dry.setValue(SlabBlock.WATERLOGGED, true), 18);

                    ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

                    shovel.setDamageValue(7);

                    CompoundTag before = shovel.save(new CompoundTag());

                    require(!PathPieces.activate(world, position, player, shovel, Direction.UP, InteractionHand.MAIN_HAND)
                            && before.equals(shovel.save(new CompoundTag())),
                            "Wet pieces cannot consume shovel durability");

                    BlockState current = world.getBlockState(position);

                    current.getBlock().tick(current, world, position, new Random(0));

                    BlockState expected = Pieces.state("minecraft:dirt", block.palette.shape, block.orientation(dry));
                    BlockState actual = world.getBlockState(position);

                    require(actual.getBlock() == expected.getBlock() && actual.getValue(SlabBlock.WATERLOGGED)
                            && ((PieceBlock) actual.getBlock()).mask(actual) == ((PieceBlock) expected.getBlock())
                                    .mask(expected),
                            "Wet grass and paths decay to matching waterlogged dirt: " + dry + " became " + actual
                                    + ", expected " + expected);
                    require(!((PieceBlock) actual.getBlock()).isValidBonemealTarget(world, position, actual, false),
                            "Wet dirt cannot grow grass through bonemeal");
                    decays++;
                }

                tested++;
            }
        }

        world.removeBlock(position, false);
        world.removeBlock(position.below(), false);
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_WATERLOGGING_PASS states=" + tested + " decays=" + decays);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
