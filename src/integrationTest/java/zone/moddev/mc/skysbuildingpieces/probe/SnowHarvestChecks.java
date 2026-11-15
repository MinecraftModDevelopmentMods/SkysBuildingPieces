package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Harvest through the survival interaction manager rather than calling getDrops directly. */
final class SnowHarvestChecks {
    static void run(WorldServer world) {
        EntityPlayerMP player = FakePlayerFactory.getMinecraft(world);
        GameType previousMode = player.interactionManager.getGameType();
        ItemStack previousTool = player.getHeldItemMainhand().copy();
        NetHandlerPlayServer previousConnection = player.connection;
        BlockPos position = new BlockPos(1540, 220, 1500);
        world.getChunk(position);
        AxisAlignedBB area = new AxisAlignedBB(position).grow(2);
        int checked = 0;
        try {
            // Forge sends block-break acknowledgements even for a local fake player.
            player.connection = new NetHandlerPlayServer(world.getMinecraftServer(),
                    new NetworkManager(EnumPacketDirection.SERVERBOUND), player) {
                @Override
                public void sendPacket(Packet<?> packet) {
                    // The disposable server has no remote client to acknowledge.
                }
            };
            player.interactionManager.setGameType(GameType.SURVIVAL);
            for (String material : new String[] { "minecraft:snow", "minecraft:packed_ice" }) {
                for (Shape shape : new Shape[] { Shape.SLAB, Shape.HORIZONTAL_STEP, Shape.CORNER }) {
                    IBlockState state = Pieces.state(material, shape, shape == Shape.SLAB ? 1 : 0);
                    require(state != null, "existing snow/ice piece is registered");
                    Block block = state.getBlock();
                    ItemStack expected = Pieces.stack(state, 1);
                    for (int toolCase = 0; toolCase < 3; toolCase++) {
                        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, area)) {
                            item.setDead();
                        }
                        ItemStack tool = toolCase != 0
                                ? new ItemStack(material.equals("minecraft:snow") ? Items.WOODEN_SHOVEL : Items.WOODEN_PICKAXE)
                                : ItemStack.EMPTY;
                        if (toolCase == 2) {
                            tool.addEnchantment(Enchantments.SILK_TOUCH, 1);
                        }
                        player.setHeldItem(EnumHand.MAIN_HAND, tool);
                        Block full = material.equals("minecraft:snow") ? Blocks.SNOW : Blocks.PACKED_ICE;
                        world.setBlockState(position, full.getDefaultState(), 2);
                        boolean nativeHarvestable = full.canHarvestBlock(world, position, player);
                        world.setBlockState(position, state, 2);
                        boolean harvestable = block.canHarvestBlock(world, position, player);
                        require(harvestable == nativeHarvestable, "native snow/ice tool requirement " + material);
                        require(player.interactionManager.tryHarvestBlock(position), "survival breaking succeeds");
                        require(world.isAirBlock(position), "harvest removes the piece");
                        int units = 0;
                        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, area)) {
                            if (item.isDead) {
                                continue;
                            }
                            require(ItemStack.areItemsEqual(item.getItem(), expected), "correct piece identity drops");
                            units += item.getItem().getCount();
                            item.setDead();
                        }
                        boolean returnsPiece = material.equals("minecraft:snow") ? toolCase != 0 : toolCase == 2;
                        require(units == (returnsPiece ? 1 : 0), "survival drop count " + material + "/" + shape);
                        checked++;
                    }
                }
            }
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND, previousTool);
            player.interactionManager.setGameType(previousMode);
            player.connection = previousConnection;
            world.setBlockToAir(position);
        }
        System.out.println("BUILDING_PIECES_SURVIVAL_SNOW_ICE_PASS checks=" + checked);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private SnowHarvestChecks() {
    }
}
