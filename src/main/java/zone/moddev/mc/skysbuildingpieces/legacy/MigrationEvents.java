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
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

public final class MigrationEvents {
    private static final MigrationEvents INSTANCE = new MigrationEvents();

    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(INSTANCE::tick);
        LevelEvent.Unload.BUS.addListener(INSTANCE::unload);
        BlockEvent.EntityPlaceEvent.BUS.addListener(INSTANCE::placed);
        EntityJoinLevelEvent.BUS.addListener(INSTANCE::entity);
        PlayerEvent.LoadFromFile.BUS.addListener(INSTANCE::player);
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

    public void tick(TickEvent.ServerTickEvent.Post event) {
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
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks
                    .getCurrentServer();

            if (server != null) {
                LegacyBridge.report(server.getLevel(Level.OVERWORLD));
            }
        }
    }

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

    public void placed(BlockEvent.EntityPlaceEvent event) {
        if (level(event.getLevel()).isClientSide() || !LegacyBridge.active()) {
            return;
        }

        BlockPos position = event.getPos();
        BlockState existingState = event.getLevel().getBlockState(position);
        String registryId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(existingState.getBlock()).toString();

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
            IItemHandler handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);

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

        var ops = world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag before = (CompoundTag) ItemStack.CODEC.encodeStart(ops, legacyStack).getOrThrow();
        // The current stack codec may omit count=1; this is a known stack, not an entity tag.
        if (!before.contains("count")) {
            before.putInt("count", legacyStack.getCount());
        }
        CompoundTag legacyTileData = before.copy();

        LegacyBridge.prepareStack(legacyTileData);

        if (legacyTileData.equals(before)) {
            return legacyStack;
        }

        return ItemStack.CODEC.parse(ops, legacyTileData).getOrThrow(message ->
                new LegacyBridge.RecoveryAbort("Cannot decode recovered item "
                        + legacyTileData.getStringOr("id", "") + ": " + message, null));
    }
}
