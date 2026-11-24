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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

public final class MigrationEvents {
    private static final MigrationEvents INSTANCE = new MigrationEvents();

    public static void register() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }
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
        // Numeric saves use a Level wrapper. Modern chunks keep these keys at the root.
        return data.getCompound("Level").orElse(data);
    }

    public static CompoundTag copyMigrationData(CompoundTag root) {
        CompoundTag source = chunkLevel(root);
        CompoundTag markers = new CompoundTag();
        for (String key : source.keySet()) {
            if (key.startsWith("skysbuildingpieces_")) {
                markers.put(key, source.get(key).copy());
            }
        }
        return markers;
    }

    public static CompoundTag copyCoverageForSave(ChunkAccess chunk) {
        CompoundTag coverage = INSTANCE.coverage.get(chunk);
        if (coverage == null) {
            return null;
        }
        CompoundTag markers = new CompoundTag();
        markers.put("skysbuildingpieces_coverage", coverage.copy());
        return markers;
    }

    public static void loadSerializedChunk(ServerLevel world, ChunkAccess chunk, CompoundTag markers) {
        CompoundTag data = markers == null ? new CompoundTag() : markers;
        data.getCompound("skysbuildingpieces_coverage")
                .ifPresent(saved -> INSTANCE.coverage.put(chunk, saved.copy()));
        if (!(chunk instanceof LevelChunk fullChunk)) {
            return;
        }
        if (!data.isEmpty() || LegacyBridge.active()) {
            chunk.markUnsaved();
            // Chunk reads can finish on a worker; saved data belongs to the server thread.
            world.getServer().execute(() -> INSTANCE.load(world, fullChunk, data));
        }
    }

    private void load(ServerLevel world, LevelChunk chunk, CompoundTag legacyTileData) {

        if (legacyTileData.contains("skysbuildingpieces_retired_delta")) {
            PiecesWorldState state = PiecesWorldState.get(world);

            state.addChunk(HistoricalFullBlocks.MODULE, legacyTileData.getLongOr("skysbuildingpieces_retired_delta", 0L));
            state.setDirty();
            legacyTileData.remove("skysbuildingpieces_retired_delta");
            chunk.markUnsaved();
        }
        if (legacyTileData.getCompound("skysbuildingpieces_historical_delta").isPresent()) {
            PiecesWorldState state = PiecesWorldState.get(world);
            CompoundTag deltas = legacyTileData.getCompoundOrEmpty("skysbuildingpieces_historical_delta");

            for (String module : deltas.keySet()) {
                state.addChunk(module, deltas.getLongOr(module, 0L));
            }

            state.setDirty();
            legacyTileData.remove("skysbuildingpieces_historical_delta");
            chunk.markUnsaved();
        }
        if (!LegacyBridge.active()) {
            return;
        }
        if (!coverage.containsKey(chunk)) {
            coverage.put(chunk, new CompoundTag());
        }

        // Raw recovery changes the compound before constructing the Chunk. That constructor does not know it must
        // persist the recovered data. Mark storage dirty explicitly, without any neighbour notifications.
        chunk.markUnsaved();

        if (legacyTileData.contains("skysbuildingpieces_delta")) {
            PiecesWorldState worldState = PiecesWorldState.get(world);

            worldState.blocks += legacyTileData.getLongOr("skysbuildingpieces_delta", 0L);
            worldState.chunks++;
            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_delta");
        }
        if (legacyTileData.contains("skysbuildingpieces_additional_vanilla_delta")) {
            // New coverage in an already counted chunk adds conversions, not a second chunk visit.
            PiecesWorldState worldState = PiecesWorldState.get(world);

            worldState.blocks += legacyTileData.getLongOr("skysbuildingpieces_additional_vanilla_delta", 0L);
            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_additional_vanilla_delta");
        }
        if (legacyTileData.getCompound("skysbuildingpieces_module_delta").isPresent()) {
            PiecesWorldState worldState = PiecesWorldState.get(world);
            CompoundTag deltas = legacyTileData.getCompoundOrEmpty("skysbuildingpieces_module_delta");

            for (String module : deltas.keySet()) {
                worldState.addChunk(module, deltas.getLongOr(module, 0L));
            }

            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_module_delta");
        }
        if (legacyTileData.getCompound("skysbuildingpieces_additional_module_delta").isPresent()) {
            PiecesWorldState worldState = PiecesWorldState.get(world);
            CompoundTag deltas = legacyTileData.getCompoundOrEmpty("skysbuildingpieces_additional_module_delta");

            for (String module : deltas.keySet()) {
                worldState.addBlocks(module, deltas.getLongOr(module, 0L));
            }

            worldState.setDirty();
            legacyTileData.remove("skysbuildingpieces_additional_module_delta");
        }

        pending.add(chunk);
        LegacyBridge.report(world);
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
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

                if (raw.getString("LootTable").isPresent()) {
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
        if (level(event.getLevel()).isClientSide()) {
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
    public void placed(BlockEvent.EntityPlaceEvent event) {
        if (level(event.getLevel()).isClientSide() || !LegacyBridge.active()) {
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
                LegacyMapping.material(legacyTileData.getStringOr("material", "")), shape,
                LegacyMapping.orientation(shape, LegacyMapping.metadata(existingState)));

        if (target != null && event.getLevel().setBlock(position, target, 3)) {
            net.minecraft.world.level.chunk.ChunkAccess chunk = event.getLevel().getChunk(position);
            CompoundTag chunkCoverage = coverage.get(chunk);

            if (chunkCoverage == null) {
                chunkCoverage = new CompoundTag();
                coverage.put(chunk, chunkCoverage);
            }

            ListTag retained = chunkCoverage.getListOrEmpty("retained_tiles");

            retained.add(legacyTileData.copy());
            chunkCoverage.put("retained_tiles", retained);

            PiecesWorldState worldState = PiecesWorldState.get(level(event.getLevel()));

            worldState.addBlocks(
                    Catalogue.INSTANCE.moduleFor(LegacyMapping.material(legacyTileData.getStringOr("material", ""))), 1);
            worldState.setDirty();
        }
    }

    @SubscribeEvent
    public void entity(EntityJoinLevelEvent event) {
        Level world = event.getLevel();
        if (world.isClientSide() || !LegacyBridge.ready) {
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
        if (world == null) {
            return;
        }
        if (tile instanceof Container) {
            inventory((Container) tile, world);
        }

        Set<ResourceHandler<ItemResource>> handled = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST }) {
            ResourceHandler<ItemResource> handler = world.getCapability(Capabilities.Item.BLOCK, tile.getBlockPos(), face);
            if (handler == null || !handled.add(handler)) {
                continue;
            }
            for (int slot = 0; slot < handler.size(); slot++) {
                if (replaceStoredItem(handler, slot, world)) {
                    tile.setChanged();
                }
            }
        }
    }

    private static boolean replaceStoredItem(ResourceHandler<ItemResource> handler, int slot, Level world) {
        ItemResource resource = handler.getResource(slot);
        long amount = handler.getAmountAsLong(slot);
        if (resource.isEmpty() || amount <= 0 || amount > Integer.MAX_VALUE) {
            return false;
        }
        int count = (int) amount;
        ItemStack before = resource.toStack(count);
        List<Runnable> accounting = new ArrayList<>();
        ItemStack updated = convert(before, world, accounting::add);
        if (updated == before) {
            return false;
        }

        // If an inventory rejects the replacement, closing the transaction restores the original stack.
        try (Transaction transaction = Transaction.openRoot()) {
            if (handler.extract(slot, resource, count, transaction) != count
                    || handler.insert(slot, ItemResource.of(updated), updated.getCount(), transaction) != updated.getCount()) {
                return false;
            }
            transaction.commit();
        }
        accounting.forEach(Runnable::run);
        return true;
    }

    public static ItemStack convert(ItemStack legacyStack, Level world) {
        return convert(legacyStack, world, Runnable::run);
    }

    private static ItemStack convert(ItemStack legacyStack, Level world, java.util.function.Consumer<Runnable> account) {
        if (legacyStack == null || legacyStack.isEmpty() || world == null || !LegacyBridge.ready) {
            return legacyStack;
        }

        return LiveItemRecovery.convert(legacyStack, world.registryAccess(), account);

    }
}
