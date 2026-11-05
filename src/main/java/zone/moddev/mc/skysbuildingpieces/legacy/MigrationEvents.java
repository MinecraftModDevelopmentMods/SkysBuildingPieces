package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

public final class MigrationEvents {
    private static Level level(net.minecraft.world.level.LevelAccessor world) {
        if (world instanceof Level) return (Level) world;
        if (world instanceof net.minecraft.world.level.WorldGenLevel) {
            return ((net.minecraft.world.level.WorldGenLevel) world).getLevel();
        }
        throw new LegacyBridge.RecoveryAbort("Expected a loaded server world for piece migration", null);
    }

    private int ticks;
    private final java.util.Queue<LevelChunk> pending = new java.util.concurrent.ConcurrentLinkedQueue<LevelChunk>();
    private final Map<net.minecraft.world.level.chunk.ChunkAccess, CompoundTag> coverage = Collections
            .synchronizedMap(new WeakHashMap<net.minecraft.world.level.chunk.ChunkAccess, CompoundTag>());

    static CompoundTag chunkLevel(CompoundTag data) {
        // Forge 36 supplies the whole chunk root. Retain direct-Level handling for fixtures.
        return data.contains("Level", 10) ? data.getCompound("Level") : data;
    }

    @SubscribeEvent
    public void load(ChunkDataEvent.Load event) {
        if (event.getLevel() != null && level(event.getLevel()).isClientSide) {
            return;
        }

        CompoundTag legacyTileData = chunkLevel(event.getData());
        CompoundTag level = legacyTileData;

        // Turning replacement off must retain existing module markers, while leaving previously unvisited chunks
        // entirely unmarked.
        if (level.contains("skysbuildingpieces_coverage", 10)) {
            coverage.put(event.getChunk(), level.getCompound("skysbuildingpieces_coverage").copy());
        }
        // Forge also posts this event for unfinished chunks, which have no World. Their raw recovery has already run;
        // only full chunks have live totals and inventories to update here.
        if (event.getLevel() == null) {
            return;
        }
        if (legacyTileData.contains("skysbuildingpieces_retired_delta")) {
            PiecesWorldState state = PiecesWorldState.get(level(event.getLevel()));

            state.addChunk(HistoricalFullBlocks.MODULE, legacyTileData.getLong("skysbuildingpieces_retired_delta"));
            state.setDirty();
            legacyTileData.remove("skysbuildingpieces_retired_delta");
            event.getChunk().setUnsaved(true);
        }
        if (legacyTileData.contains("skysbuildingpieces_historical_delta", 10)) {
            PiecesWorldState state = PiecesWorldState.get(level(event.getLevel()));
            CompoundTag deltas = legacyTileData.getCompound("skysbuildingpieces_historical_delta");

            for (String module : deltas.getAllKeys()) {
                state.addChunk(module, deltas.getLong(module));
            }

            state.setDirty();
            legacyTileData.remove("skysbuildingpieces_historical_delta");
            event.getChunk().setUnsaved(true);
        }
        if (!LegacyBridge.active()) {
            return;
        }
        if (!coverage.containsKey(event.getChunk())) {
            coverage.put(event.getChunk(), new CompoundTag());
        }

        // Raw recovery changes the compound before constructing the Chunk. That constructor does not know it must
        // persist the recovered data. Mark storage dirty explicitly, without any neighbour notifications.
        event.getChunk().setUnsaved(true);

        if (legacyTileData.contains("skysbuildingpieces_delta")) {
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getLevel()));

            worldState.blocks += legacyTileData.getLong("skysbuildingpieces_delta");
            worldState.chunks++;
            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_delta");
        }
        if (legacyTileData.contains("skysbuildingpieces_additional_vanilla_delta")) {
            // New coverage in an already counted chunk adds conversions, not a second chunk visit.
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getLevel()));

            worldState.blocks += legacyTileData.getLong("skysbuildingpieces_additional_vanilla_delta");
            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_additional_vanilla_delta");
        }
        if (legacyTileData.contains("skysbuildingpieces_module_delta", 10)) {
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getLevel()));
            CompoundTag deltas = legacyTileData.getCompound("skysbuildingpieces_module_delta");

            for (String module : deltas.getAllKeys()) {
                worldState.addChunk(module, deltas.getLong(module));
            }

            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_module_delta");
        }
        if (legacyTileData.contains("skysbuildingpieces_additional_module_delta", 10)) {
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getLevel()));
            CompoundTag deltas = legacyTileData.getCompound("skysbuildingpieces_additional_module_delta");

            for (String module : deltas.getAllKeys()) {
                worldState.addBlocks(module, deltas.getLong(module));
            }

            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_additional_module_delta");
        }

        if (event.getChunk() instanceof LevelChunk) {
            pending.add((LevelChunk) event.getChunk());
        }

        LegacyBridge.report(level(event.getLevel()));
    }

    @SubscribeEvent
    public void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!LegacyBridge.ready) {
            return;
        }
        for (int count = 0; count < 8; count++) {
            LevelChunk chunk = pending.poll();

            if (chunk == null) {
                break;
            }
            for (BlockEntity tile : new ArrayList<BlockEntity>(chunk.getBlockEntities().values())) {
                // Unopened loot containers must not have their loot generated by migration.
                CompoundTag raw = tile.saveWithFullMetadata(tile.getLevel().registryAccess());

                if (raw.contains("LootTable", 8)) {
                    continue;
                }

                containers(tile);
            }
        }
        if (++ticks % 100 == 0 && LegacyBridge.hasPendingReport()) {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks
                    .getCurrentServer();

            if (server != null) {
                LegacyBridge.report(server.getLevel(Level.OVERWORLD));
            }
        }
    }

    @SubscribeEvent
    public void unload(LevelEvent.Unload event) {
        if (level(event.getLevel()).isClientSide) {
            return;
        }
        if (LegacyBridge.recoveryActive() && LegacyBridge.hasPendingReport()) {
            LegacyBridge.report(level(event.getLevel()));
        }

        pending.removeIf(chunk -> chunk instanceof LevelChunk && ((LevelChunk) chunk).getLevel() == event.getLevel());

        synchronized (coverage) {
            coverage.keySet()
                    .removeIf(chunk -> chunk instanceof LevelChunk && ((LevelChunk) chunk).getLevel() == event.getLevel());
        }
    }

    @SubscribeEvent
    public void save(ChunkDataEvent.Save event) {
        CompoundTag chunkCoverage = coverage.get(event.getChunk());

        if (chunkCoverage != null) {
            chunkLevel(event.getData()).put("skysbuildingpieces_coverage", chunkCoverage.copy());
        }
    }

    @SubscribeEvent
    public void placed(BlockEvent.EntityPlaceEvent event) {
        if (level(event.getLevel()).isClientSide || !LegacyBridge.active()) {
            return;
        }

        BlockPos position = event.getPos();
        BlockState existingState = event.getLevel().getBlockState(position);
        String registryId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(existingState.getBlock()).toString();

        if (!LegacyMapping.owned(registryId)) {
            return;
        }

        BlockEntity tile = event.getLevel().getBlockEntity(position);

        if (tile == null) {
            return;
        }

        CompoundTag legacyTileData = tile.saveWithFullMetadata(tile.getLevel().registryAccess());
        Shape shape = LegacyMapping.shape(registryId, LegacyMapping.metadata(existingState));
        BlockState target = LegacyBridge.resolve(registryId,
                LegacyMapping.material(legacyTileData.getString("material")), shape,
                LegacyMapping.orientation(shape, LegacyMapping.metadata(existingState)));

        if (target != null && event.getLevel().setBlock(position, target, 3)) {
            net.minecraft.world.level.chunk.ChunkAccess chunk = event.getLevel().getChunk(position);
            CompoundTag chunkCoverage = coverage.get(chunk);

            if (chunkCoverage == null) {
                chunkCoverage = new CompoundTag();
                coverage.put(chunk, chunkCoverage);
            }

            ListTag retained = chunkCoverage.getList("retained_tiles", 10);

            retained.add(legacyTileData.copy());
            chunkCoverage.put("retained_tiles", retained);

            PiecesWorldState worldState = PiecesWorldState.get(level(event.getLevel()));

            worldState.addBlocks(
                    Catalogue.INSTANCE.moduleFor(LegacyMapping.material(legacyTileData.getString("material"))), 1);
            worldState.setDirty();
        }
    }

    @SubscribeEvent
    public void entity(EntityJoinLevelEvent event) {
        Level world = event.getLevel();
        if (world.isClientSide || !LegacyBridge.ready) {
            return;
        }

        Entity entity = event.getEntity();

        if (entity instanceof Player) {
            Player player = (Player) entity;

            inventory(player.getInventory(), world);
            inventory(player.getEnderChestInventory(), world);
        }
        if (entity instanceof ItemEntity) {
            ItemEntity item = (ItemEntity) entity;

            item.setItem(convert(item.getItem(), world));
        }
        if (entity instanceof ItemFrame) {
            ItemFrame itemFrame = (ItemFrame) entity;
            ItemStack original = itemFrame.getItem();
            ItemStack updated = convert(original, world);

            if (updated != original) {
                // Entity loading runs before its chunk future completes. Vanilla
                // uses this same path to restore a frame without reading comparators.
                itemFrame.setItem(updated, false);
            }
        }
        if (entity instanceof Container) {
            inventory((Container) entity, world);
        }
    }

    @SubscribeEvent
    public void player(PlayerEvent.LoadFromFile event) {
        if (LegacyBridge.recoveryActive()) {
            Player player = event.getEntity();
            inventory(player.getInventory(), player.level());
            inventory(player.getEnderChestInventory(), player.level());
        }
    }

    private static void inventory(Container inventory, Level world) {
        for (int slotIndex = 0; slotIndex < inventory.getContainerSize(); slotIndex++) {
            ItemStack legacyStack = inventory.getItem(slotIndex);
            ItemStack updated = convert(legacyStack, world);

            if (updated != legacyStack) {
                inventory.setItem(slotIndex, updated);
                inventory.setChanged();
            }
        }
    }

    private static void containers(BlockEntity tile) {
        Level world = tile.getLevel();
        if (tile instanceof Container) {
            inventory((Container) tile, world);
        }

        Set<IItemHandler> handled = Collections.newSetFromMap(new IdentityHashMap<IItemHandler, Boolean>());

        for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST }) {
            IItemHandler handler = world == null ? null : world.getCapability(Capabilities.ItemHandler.BLOCK, tile.getBlockPos(), face);

            if (handler != null) {

                if (handler instanceof IItemHandlerModifiable && handled.add(handler)) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack legacyStack = handler.getStackInSlot(slot);
                        ItemStack updated = convert(legacyStack, world);

                        if (updated != legacyStack) {
                            ((IItemHandlerModifiable) handler).setStackInSlot(slot, updated);
                            tile.setChanged();
                        }
                    }
                }
            }
        }
    }

    public static ItemStack convert(ItemStack legacyStack, Level world) {
        if (legacyStack == null || legacyStack.isEmpty() || world == null || !LegacyBridge.ready) {
            return legacyStack;
        }

        CompoundTag before = (CompoundTag) legacyStack.save(world.registryAccess());
        // The current stack codec may omit count=1; this is a known stack, not an entity tag.
        if (!before.contains("count")) {
            before.putInt("count", legacyStack.getCount());
        }
        CompoundTag legacyTileData = before.copy();

        LegacyBridge.prepareStack(legacyTileData);

        if (legacyTileData.equals(before)) {
            return legacyStack;
        }

        return ItemStack.parse(world.registryAccess(), legacyTileData).orElseThrow(() ->
                new LegacyBridge.RecoveryAbort("Cannot decode recovered item " + legacyTileData.getString("id"), null));
    }
}
