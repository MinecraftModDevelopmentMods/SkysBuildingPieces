package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

public final class MigrationEvents {
    private static World level(net.minecraft.world.IWorld world) {
        if (world instanceof World) return (World) world;
        if (world instanceof net.minecraft.world.ISeedReader) {
            return ((net.minecraft.world.ISeedReader) world).getLevel();
        }
        throw new LegacyBridge.RecoveryAbort("Expected a loaded server world for piece migration", null);
    }

    private int ticks;
    private final java.util.Queue<Chunk> pending = new java.util.concurrent.ConcurrentLinkedQueue<Chunk>();
    private final Map<net.minecraft.world.chunk.IChunk, CompoundNBT> coverage = Collections
            .synchronizedMap(new WeakHashMap<net.minecraft.world.chunk.IChunk, CompoundNBT>());

    static CompoundNBT chunkLevel(CompoundNBT data) {
        // Forge 36 supplies the whole chunk root. Retain direct-Level handling for fixtures.
        return data.contains("Level", 10) ? data.getCompound("Level") : data;
    }

    @SubscribeEvent
    public void load(ChunkDataEvent.Load event) {
        if (event.getWorld() != null && level(event.getWorld()).isClientSide) {
            return;
        }

        CompoundNBT legacyTileData = chunkLevel(event.getData());
        CompoundNBT level = legacyTileData;

        // Turning replacement off must retain existing module markers, while leaving previously unvisited chunks
        // entirely unmarked.
        if (level.contains("skysbuildingpieces_coverage", 10)) {
            coverage.put(event.getChunk(), level.getCompound("skysbuildingpieces_coverage").copy());
        }
        // Forge also posts this event for unfinished chunks, which have no World. Their raw recovery has already run;
        // only full chunks have live totals and inventories to update here.
        if (event.getWorld() == null) {
            return;
        }
        if (legacyTileData.contains("skysbuildingpieces_retired_delta")) {
            PiecesWorldState state = PiecesWorldState.get(level(event.getWorld()));

            state.addChunk(HistoricalFullBlocks.MODULE, legacyTileData.getLong("skysbuildingpieces_retired_delta"));
            state.setDirty();
            legacyTileData.remove("skysbuildingpieces_retired_delta");
            event.getChunk().setUnsaved(true);
        }
        if (legacyTileData.contains("skysbuildingpieces_historical_delta", 10)) {
            PiecesWorldState state = PiecesWorldState.get(level(event.getWorld()));
            CompoundNBT deltas = legacyTileData.getCompound("skysbuildingpieces_historical_delta");

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
            coverage.put(event.getChunk(), new CompoundNBT());
        }

        // Raw recovery changes the compound before constructing the Chunk. That constructor does not know it must
        // persist the recovered data. Mark storage dirty explicitly, without any neighbour notifications.
        event.getChunk().setUnsaved(true);

        if (legacyTileData.contains("skysbuildingpieces_delta")) {
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getWorld()));

            worldState.blocks += legacyTileData.getLong("skysbuildingpieces_delta");
            worldState.chunks++;
            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_delta");
        }
        if (legacyTileData.contains("skysbuildingpieces_additional_vanilla_delta")) {
            // New coverage in an already counted chunk adds conversions, not a second chunk visit.
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getWorld()));

            worldState.blocks += legacyTileData.getLong("skysbuildingpieces_additional_vanilla_delta");
            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_additional_vanilla_delta");
        }
        if (legacyTileData.contains("skysbuildingpieces_module_delta", 10)) {
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getWorld()));
            CompoundNBT deltas = legacyTileData.getCompound("skysbuildingpieces_module_delta");

            for (String module : deltas.getAllKeys()) {
                worldState.addChunk(module, deltas.getLong(module));
            }

            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_module_delta");
        }
        if (legacyTileData.contains("skysbuildingpieces_additional_module_delta", 10)) {
            PiecesWorldState worldState = PiecesWorldState.get(level(event.getWorld()));
            CompoundNBT deltas = legacyTileData.getCompound("skysbuildingpieces_additional_module_delta");

            for (String module : deltas.getAllKeys()) {
                worldState.addBlocks(module, deltas.getLong(module));
            }

            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_additional_module_delta");
        }

        if (event.getChunk() instanceof Chunk) {
            pending.add((Chunk) event.getChunk());
        }

        LegacyBridge.report(level(event.getWorld()));
    }

    @SubscribeEvent
    public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !LegacyBridge.ready) {
            return;
        }
        for (int count = 0; count < 8; count++) {
            Chunk chunk = pending.poll();

            if (chunk == null) {
                break;
            }
            for (TileEntity tile : new ArrayList<TileEntity>(chunk.getBlockEntities().values())) {
                // Unopened loot containers must not have their loot generated by migration.
                CompoundNBT raw = tile.save(new CompoundNBT());

                if (raw.contains("LootTable", 8)) {
                    continue;
                }

                containers(tile);
            }
        }
        if (++ticks % 100 == 0 && LegacyBridge.hasPendingReport()) {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks
                    .getCurrentServer();

            if (server != null) {
                LegacyBridge.report(server.getLevel(World.OVERWORLD));
            }
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (level(event.getWorld()).isClientSide) {
            return;
        }
        if (LegacyBridge.recoveryActive() && LegacyBridge.hasPendingReport()) {
            LegacyBridge.report(level(event.getWorld()));
        }

        pending.removeIf(chunk -> chunk instanceof Chunk && ((Chunk) chunk).getLevel() == event.getWorld());

        synchronized (coverage) {
            coverage.keySet()
                    .removeIf(chunk -> chunk instanceof Chunk && ((Chunk) chunk).getLevel() == event.getWorld());
        }
    }

    @SubscribeEvent
    public void save(ChunkDataEvent.Save event) {
        CompoundNBT chunkCoverage = coverage.get(event.getChunk());

        if (chunkCoverage != null) {
            event.getData().getCompound("Level").put("skysbuildingpieces_coverage", chunkCoverage.copy());
        }
    }

    @SubscribeEvent
    public void placed(BlockEvent.EntityPlaceEvent event) {
        if (level(event.getWorld()).isClientSide || !LegacyBridge.active()) {
            return;
        }

        BlockPos position = event.getPos();
        BlockState existingState = event.getWorld().getBlockState(position);
        String registryId = existingState.getBlock().getRegistryName().toString();

        if (!LegacyMapping.owned(registryId)) {
            return;
        }

        TileEntity tile = event.getWorld().getBlockEntity(position);

        if (tile == null) {
            return;
        }

        CompoundNBT legacyTileData = tile.save(new CompoundNBT());
        Shape shape = LegacyMapping.shape(registryId, LegacyMapping.metadata(existingState));
        BlockState target = LegacyBridge.resolve(registryId,
                LegacyMapping.material(legacyTileData.getString("material")), shape,
                LegacyMapping.orientation(shape, LegacyMapping.metadata(existingState)));

        if (target != null && event.getWorld().setBlock(position, target, 3)) {
            net.minecraft.world.chunk.IChunk chunk = event.getWorld().getChunk(position);
            CompoundNBT chunkCoverage = coverage.get(chunk);

            if (chunkCoverage == null) {
                chunkCoverage = new CompoundNBT();
                coverage.put(chunk, chunkCoverage);
            }

            ListNBT retained = chunkCoverage.getList("retained_tiles", 10);

            retained.add(legacyTileData.copy());
            chunkCoverage.put("retained_tiles", retained);

            PiecesWorldState worldState = PiecesWorldState.get(level(event.getWorld()));

            worldState.addBlocks(
                    Catalogue.INSTANCE.moduleFor(LegacyMapping.material(legacyTileData.getString("material"))), 1);
            worldState.setDirty();
        }
    }

    @SubscribeEvent
    public void entity(EntityJoinWorldEvent event) {
        if (level(event.getWorld()).isClientSide || !LegacyBridge.ready) {
            return;
        }

        Entity entity = event.getEntity();

        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;

            inventory(player.inventory);
            inventory(player.getEnderChestInventory());
        }
        if (entity instanceof ItemEntity) {
            ItemEntity item = (ItemEntity) entity;

            item.setItem(convert(item.getItem(), event.getWorld()));
        }
        if (entity instanceof ItemFrameEntity) {
            ItemFrameEntity itemFrame = (ItemFrameEntity) entity;
            ItemStack original = itemFrame.getItem();
            ItemStack updated = convert(original, event.getWorld());

            if (updated != original) {
                // Entity loading runs before its chunk future completes. Vanilla
                // uses this same path to restore a frame without reading comparators.
                itemFrame.setItem(updated, false);
            }
        }
        if (entity instanceof IInventory) {
            inventory((IInventory) entity);
        }
    }

    @SubscribeEvent
    public void player(PlayerEvent.LoadFromFile event) {
        if (LegacyBridge.recoveryActive()) {
            inventory(event.getPlayer().inventory);
            inventory(event.getPlayer().getEnderChestInventory());
        }
    }

    private static void inventory(IInventory inventory) {
        for (int slotIndex = 0; slotIndex < inventory.getContainerSize(); slotIndex++) {
            ItemStack legacyStack = inventory.getItem(slotIndex);
            ItemStack updated = convert(legacyStack, null);

            if (updated != legacyStack) {
                inventory.setItem(slotIndex, updated);
                inventory.setChanged();
            }
        }
    }

    private static void containers(TileEntity tile) {
        if (tile instanceof IInventory) {
            inventory((IInventory) tile);
        }

        Set<IItemHandler> handled = Collections.newSetFromMap(new IdentityHashMap<IItemHandler, Boolean>());

        for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST }) {
            IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face).orElse(null);

            if (handler != null) {

                if (handler instanceof IItemHandlerModifiable && handled.add(handler)) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack legacyStack = handler.getStackInSlot(slot);
                        ItemStack updated = convert(legacyStack, tile.getLevel());

                        if (updated != legacyStack) {
                            ((IItemHandlerModifiable) handler).setStackInSlot(slot, updated);
                            tile.setChanged();
                        }
                    }
                }
            }
        }
    }

    public static ItemStack convert(ItemStack legacyStack, net.minecraft.world.World world) {
        if (legacyStack == null || legacyStack.isEmpty() || !LegacyBridge.ready) {
            return legacyStack;
        }

        CompoundNBT before = legacyStack.save(new CompoundNBT());
        CompoundNBT legacyTileData = before.copy();

        LegacyBridge.prepareStack(legacyTileData);

        if (legacyTileData.equals(before)) {
            return legacyStack;
        }

        ItemStack updated = ItemStack.of(legacyTileData);

        return updated;
    }
}
