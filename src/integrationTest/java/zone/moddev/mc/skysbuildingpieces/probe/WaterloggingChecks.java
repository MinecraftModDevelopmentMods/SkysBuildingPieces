package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.content.PathPieces;

/** Uses the target's fluid-container contract for every assigned piece state. */
public final class WaterloggingChecks {
    private WaterloggingChecks() {
    }

    public static void run(ServerWorld world) {
        BlockPos position = new BlockPos(1158, 180, 1158);

        world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);
        world.removeBlock(position.up(), false);

        ServerPlayerEntity player = FakePlayerFactory.getMinecraft(world);
        int tested = 0;
        int decays = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            int assigned = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < assigned; metadata++) {
                BlockState dry = block.getStateFromMeta(metadata);
                String material = block.definition(dry).id;

                world.setBlockState(position, dry, 18);
                require(block.canContainFluid(world, position, dry, Fluids.WATER), "Dry piece accepts water");
                require(!block.canContainFluid(world, position, dry, Fluids.LAVA), "Pieces reject lava waterlogging");
                require(block.receiveFluid(world, position, dry, Fluids.WATER.getStillFluidState(false)),
                        "Water enters the native fluid container");

                BlockState wet = world.getBlockState(position);
                BlockState wetDirt = material.equals("minecraft:grass") || material.equals("minecraft:grass_path")
                        ? Pieces.state("minecraft:dirt", block.palette.shape, block.orientation(dry))
                        : null;
                boolean preserved = wet.getBlock() == block && block.getMetaFromState(wet) == metadata;
                boolean decayed = wetDirt != null && wet.getBlock() == wetDirt.getBlock()
                        && ((PieceBlock) wet.getBlock()).mask(wet) == ((PieceBlock) wetDirt.getBlock()).mask(wetDirt);

                require((preserved || decayed) && wet.get(SlabBlock.WATERLOGGED)
                        && wet.getFluidState().getFluid() == Fluids.WATER,
                        "Waterlogging preserves geometry, allowing wet soil decay: " + dry + " became " + wet);
                require(!block.canContainFluid(world, position, wet, Fluids.WATER)
                        && !block.receiveFluid(world, position, wet, Fluids.WATER.getStillFluidState(false)),
                        "An already wet piece rejects another water placement");
                require(world.getPendingFluidTicks().isTickScheduled(position, Fluids.WATER),
                        "Waterlogging schedules the native fluid tick");
                require(block.pickupFluid(world, position, wet) == Fluids.WATER
                        && !world.getBlockState(position).get(SlabBlock.WATERLOGGED),
                        "Bucket pickup leaves the same dry piece");
                require(block.pickupFluid(world, position, world.getBlockState(position)) == Fluids.EMPTY,
                        "Water cannot be collected twice");

                if (material.equals("minecraft:grass") || material.equals("minecraft:grass_path")) {
                    world.setBlockState(position, dry.with(SlabBlock.WATERLOGGED, true), 18);

                    ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

                    shovel.setDamage(7);

                    CompoundNBT before = shovel.write(new CompoundNBT());

                    require(!PathPieces.activate(world, position, player, shovel, Direction.UP, Hand.MAIN_HAND)
                            && before.equals(shovel.write(new CompoundNBT())),
                            "Wet pieces cannot consume shovel durability");

                    BlockState current = world.getBlockState(position);

                    current.getBlock().tick(current, world, position, new Random(0));

                    BlockState expected = Pieces.state("minecraft:dirt", block.palette.shape, block.orientation(dry));
                    BlockState actual = world.getBlockState(position);

                    require(actual.getBlock() == expected.getBlock() && actual.get(SlabBlock.WATERLOGGED)
                            && ((PieceBlock) actual.getBlock()).mask(actual) == ((PieceBlock) expected.getBlock())
                                    .mask(expected),
                            "Wet grass and paths decay to matching waterlogged dirt: " + dry + " became " + actual
                                    + ", expected " + expected);
                    require(!((PieceBlock) actual.getBlock()).canGrow(world, position, actual, false),
                            "Wet dirt cannot grow grass through bonemeal");
                    decays++;
                }

                tested++;
            }
        }

        world.removeBlock(position, false);
        world.removeBlock(position.down(), false);
        System.out.println("BUILDING_PIECES_WATERLOGGING_PASS states=" + tested + " decays=" + decays);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
