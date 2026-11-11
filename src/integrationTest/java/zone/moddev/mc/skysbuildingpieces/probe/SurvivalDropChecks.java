package zone.moddev.mc.skysbuildingpieces.probe;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Breaks real blocks through survival mode, including the tool gate before loot is evaluated. */
final class SurvivalDropChecks {
    static void run(ServerLevel world) {
        ServerPlayer player = FakePlayerFactory.get(world,
                new GameProfile(UUID.fromString("7d233ad9-fb31-4cb4-b321-4c42d4127399"), "SurvivalDrops"));
        player.setGameMode(GameType.SURVIVAL);
        require(player.gameMode.isSurvival(), "The drop audit must run in survival");

        BlockPos position = new BlockPos(-500, 90, -500);
        AABB collectionArea = new AABB(position).inflate(1);
        world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 2);
        List<String> failures = new ArrayList<>();
        int cases = 0;

        for (PieceBlock block : Pieces.playableBlocks()) {
            BlockState reference = block.getStateFromMeta(0);
            var material = block.definition(reference);
            int orientations = material.type.equals("snow") ? block.palette.shape.states : 1;
            for (int orientation = 0; orientation < orientations; orientation++) {
                for (int toolCase = 0; toolCase < 5; toolCase++) {
                    ItemStack tool = switch (toolCase) {
                        case 0 -> ItemStack.EMPTY;
                        case 1 -> new ItemStack(Items.IRON_SHOVEL);
                        case 2 -> new ItemStack(Items.WOODEN_PICKAXE);
                        default -> new ItemStack(Items.DIAMOND_PICKAXE);
                    };
                    if (toolCase == 4) {
                        tool.enchant(world.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
                    }
                    for (ItemEntity entity : world.getEntitiesOfClass(ItemEntity.class, collectionArea)) {
                        entity.discard();
                    }
                    world.setBlock(position, block.getStateFromMeta(orientation), 2);
                    BlockState state = world.getBlockState(position);
                    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                    BlockState full = Pieces.nativeState(material, "full");
                    // Small snow pieces are recoverable by hand; other materials keep their native tool tiers.
                    boolean harvest = material.type.equals("snow") || full.canHarvestBlock(world, position, player);
                    List<ItemStack> expected = harvest
                            ? Block.getDrops(state, world, position, null, player, tool.copy()) : List.of();
                    require(player.gameMode.destroyBlock(position), "Survival breaking failed for " + state);
                    require(world.getBlockState(position).isAir(), "Breaking left a piece behind: " + state);
                    List<ItemStack> actual = world.getEntitiesOfClass(ItemEntity.class, collectionArea)
                            .stream().map(entity -> entity.getItem().copy()).toList();
                    if (!sameDrops(expected, actual)) {
                        failures.add(state + " tool=" + toolCase + " expected=" + expected + " actual=" + actual);
                    }
                    cases++;
                }
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (ItemEntity entity : world.getEntitiesOfClass(ItemEntity.class, collectionArea)) {
            entity.discard();
        }
        require(failures.isEmpty(), "Survival drop audit found " + failures.size() + " failures: "
                + failures.stream().limit(8).toList());
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_SURVIVAL_DROPS_PASS cases=" + cases);
    }

    private static boolean sameDrops(List<ItemStack> expected, List<ItemStack> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        List<ItemStack> remaining = new ArrayList<>(actual);
        for (ItemStack wanted : expected) {
            int match = -1;
            for (int index = 0; index < remaining.size(); index++) {
                if (ItemStack.matches(wanted, remaining.get(index))) {
                    match = index;
                    break;
                }
            }
            if (match < 0) {
                return false;
            }
            remaining.remove(match);
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

