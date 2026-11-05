package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.content.LegacyPieceRecipe;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.probe.legacy.LegacyTestScope;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

/** Real native events with an explicitly labelled build-only legacy policy mod. */
public final class CompatibilityChecks {
    private CompatibilityChecks() {
    }

    public static ItemStack legacy(int count) {
        ItemStack stack = new ItemStack(
                ForgeRegistries.ITEMS.getValue(Identifier.parse("buildingbricks:rock_step")), count);

        zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.setCustom(stack, NativeLegacyChecks.stack("minecraft:stone", count).getCompoundOrEmpty("tag").copy());
        return stack;
    }

    public static void run(ServerLevel world) throws Exception {
        if (!ModList.get().isLoaded("buildingbricks")) {
            throw new AssertionError("Installed-mod policy checks require their build-only fixture");
        }

        boolean original = SkysBuildingPieces.forceReplace;

        try (LegacyTestScope source = new LegacyTestScope(world)) {
            for (boolean convert : new boolean[] { false, true }) {
                SkysBuildingPieces.forceReplace = convert;
                liveInventories(world, convert);
                newlyPlaced(world, convert);
                unknownMaterial(world);
            }
        } finally {
            SkysBuildingPieces.forceReplace = original;
        }

        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_COMPATIBILITY_PASS installed_fixture=true replacement_off=true replacement_on=true");
    }

    private static void identity(ItemStack stack, int count, boolean converted) {
        String namespace = converted ? "skysbuildingpieces" : "buildingbricks";

        NativeLegacyChecks.require(
                !stack.isEmpty() && stack.getCount() == count
                        && net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals(namespace),
                "Live stack replacement policy: expected " + namespace + " x" + count + ", found " + stack);
        NativeLegacyChecks.require(zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.hasCustom(stack) && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(stack).equals(NativeProbeItems.custom(legacy(count))),
                "Complete unrelated custom data is retained");
    }

    private static void liveInventories(ServerLevel world, boolean convert) throws Exception {
        ServerPlayer player = NativeProbeItems.player(world);

        player.getInventory().setItem(0, legacy(13));
        player.getEnderChestInventory().setItem(0, legacy(11));
        EntityJoinLevelEvent.BUS.post(new EntityJoinLevelEvent(player, world));
        identity(player.getInventory().getItem(0), 13, convert);
        identity(player.getEnderChestInventory().getItem(0), 11, convert);
        player.getInventory().setItem(1, legacy(9));
        PlayerEvent.LoadFromFile.BUS.post(new PlayerEvent.LoadFromFile(player, world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                player.getUUID().toString()));
        identity(player.getInventory().getItem(1), 9, convert);
        world.getChunk(new BlockPos(930, 70, 930));

        ItemEntity drop = new ItemEntity(world, 930, 70, 930, legacy(5));

        NativeLegacyChecks.require(world.addFreshEntity(drop), "Dropped item joins the actual native world");
        identity(drop.getItem(), 5, convert);

        BlockPos position = new BlockPos(930, 70, 932);

        world.setBlock(position.north(), Blocks.STONE.defaultBlockState(), 2);

        ItemFrame frame = new ItemFrame(world, position, Direction.SOUTH);

        frame.setItem(legacy(1));
        world.addFreshEntity(frame);
        identity(frame.getItem(), 1, convert);

        HandlerTile tile = new HandlerTile(position);

        tile.setLevel(world);
        tile.handler.setStackInSlot(0, legacy(17));

        Method scan = MigrationEvents.class.getDeclaredMethod("containers", BlockEntity.class);

        scan.setAccessible(true);
        scan.invoke(null, tile);
        identity(tile.handler.getStackInSlot(0), 17, convert);

        CompoundTag once = tile.handler.serializeNBT(world.registryAccess());

        scan.invoke(null, tile);
        NativeLegacyChecks.require(once.equals(tile.handler.serializeNBT(world.registryAccess())),
                "Shared handler faces convert only once");

        BlockPos chestPosition = position.east();
        world.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 2);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getBlockEntity(chestPosition);
        NativeLegacyChecks.require(chest != null, "Policy fixture chest is installed");
        chest.setItem(0, legacy(19));
        scan.invoke(null, chest);
        identity(chest.getItem(0), 19, convert);
        chest.clearContent();
        world.removeBlock(chestPosition, false);

        ItemStack crate = new ItemStack(Blocks.CHEST);
        CompoundTag tag = new CompoundTag();

        tag.putBoolean("Sealed", true);

        ListTag contents = new ListTag();

        contents.add(NativeProbeItems.save(legacy(7)));
        tag.put("Items", contents);
        zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.setCustom(crate, tag);

        ItemStack migrated = MigrationEvents.convert(crate, world);
        CompoundTag recovered = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(migrated).getListOrEmpty("Items").getCompoundOrEmpty(0);

        identity(zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(recovered), 7, convert);
        NativeLegacyChecks.require(migrated.getItem() == crate.getItem() && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(migrated).getBooleanOr("Sealed", false),
                "Sealed container identity and custom data");
        NativeLegacyChecks.require(MigrationEvents.convert(migrated, world) == migrated,
                "Live nested inventory is idempotent");

        CraftingContainer grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override
            public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            @Override
            public boolean stillValid(Player ignored) {
                return true;
            }
        }, 3, 3);

        grid.setItem(0, legacy(1));

        LegacyPieceRecipe recipe = new LegacyPieceRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.BUILDING);

        NativeLegacyChecks.require(recipe.matches(grid.asCraftInput(), world),
                "Explicit bridge recipe works independently of replacement");
        identity(recipe.assemble(grid.asCraftInput(), net.minecraft.core.RegistryAccess.EMPTY), 1, true);
        drop.discard();
        frame.discard();
        world.removeBlock(position.north(), false);
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    private static void newlyPlaced(ServerLevel world, boolean convert) {
        BlockPos position = new BlockPos(934, 70, 932);
        Block block = ForgeRegistries.BLOCKS.getValue(Identifier.parse("buildingbricks:rock_step"));
        BlockState old = block.defaultBlockState().setValue((IntegerProperty) block.getStateDefinition().getProperty("meta"),
                11);

        world.setBlock(position, old, 2);

        BlockEntity tile = world.getBlockEntity(position);
        CompoundTag tag = tile.saveWithFullMetadata(world.registryAccess());

        tag.putString("material", "minecraft:stone");
        tag.putString("custom", "Keep the old tile in coverage");
        tile.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, world.registryAccess(), tag));
        BlockEvent.EntityPlaceEvent.BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(world.dimension(), world, position),
                Blocks.STONE.defaultBlockState(), NativeProbeItems.player(world)));
        NativeLegacyChecks.require(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(world.getBlockState(position).getBlock()).getNamespace()
                .equals(convert ? "skysbuildingpieces" : "buildingbricks"), "New placement replacement policy");
        NativeLegacyChecks.require((world.getBlockEntity(position) == null) == convert,
                "Remove the material tile only after successful conversion");
        world.removeBlock(position, false);
    }

    private static void unknownMaterial(ServerLevel world) {
        ItemStack unknown = legacy(3);

        CompoundTag unknownData = NativeProbeItems.custom(unknown);
        unknownData.putString("material", "missing:unknown");
        NativeProbeItems.setCustom(unknown, unknownData);

        CompoundTag before = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.save(unknown);
        ItemStack result = MigrationEvents.convert(unknown, world);

        NativeLegacyChecks.require(zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.save(result).equals(before),
                "Installed legacy mod retains unsupported materials");

        // Horizontal soil recovery belongs to Grass Slabs and must stay outside this bridge.
        CompoundTag soil = NativeLegacyChecks.stack("minecraft:dirt", 7);

        soil.putString("id", "buildingbricks:dirt_slab");
        before = soil.copy();
        LegacyBridge.prepareStack(soil);
        NativeLegacyChecks.require(soil.equals(before), "Core must not take ownership of horizontal soil slabs");
    }

    public static final class HandlerTile extends BlockEntity {
        final ItemStackHandler handler = new ItemStackHandler(2);
        private final LazyOptional<ItemStackHandler> optional = LazyOptional.of(() -> handler);

        HandlerTile(BlockPos position) {
            super(BlockEntityType.CHEST, position, Blocks.CHEST.defaultBlockState());
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == ForgeCapabilities.ITEM_HANDLER ? optional.cast()
                    : super.getCapability(capability, side);
        }
    }
}
