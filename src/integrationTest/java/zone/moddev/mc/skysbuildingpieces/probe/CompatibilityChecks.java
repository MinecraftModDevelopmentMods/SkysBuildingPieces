package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.state.IntegerProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.content.LegacyPieceRecipe;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyTestScope;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

/** Real native events with an explicitly labelled build-only legacy policy mod. */
public final class CompatibilityChecks {
    private CompatibilityChecks() {
    }

    public static ItemStack legacy(int count) {
        ItemStack stack = new ItemStack(
                ForgeRegistries.ITEMS.getValue(new ResourceLocation("buildingbricks:rock_step")), count);

        stack.setTag(NativeLegacyChecks.stack("minecraft:stone", count).getCompound("tag").copy());
        return stack;
    }

    public static void run(ServerWorld world) throws Exception {
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

        System.out.println(
                "BUILDING_PIECES_COMPATIBILITY_PASS installed_fixture=true replacement_off=true replacement_on=true");
    }

    private static void identity(ItemStack stack, int count, boolean converted) {
        String namespace = converted ? "skysbuildingpieces" : "buildingbricks";

        NativeLegacyChecks.require(
                !stack.isEmpty() && stack.getCount() == count
                        && stack.getItem().getRegistryName().getNamespace().equals(namespace),
                "Live stack replacement policy");
        NativeLegacyChecks.require(stack.hasTag() && stack.getTag().equals(legacy(count).getTag()),
                "Complete unrelated custom data is retained");
    }

    private static void liveInventories(ServerWorld world, boolean convert) throws Exception {
        ServerPlayerEntity player = FakePlayerFactory.getMinecraft(world);

        player.inventory.setInventorySlotContents(0, legacy(13));
        player.getInventoryEnderChest().setInventorySlotContents(0, legacy(11));
        MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(player, world));
        identity(player.inventory.getStackInSlot(0), 13, convert);
        identity(player.getInventoryEnderChest().getStackInSlot(0), 11, convert);
        player.inventory.setInventorySlotContents(1, legacy(9));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.LoadFromFile(player, world.getSaveHandler().getWorldDirectory(),
                player.getUniqueID().toString()));
        identity(player.inventory.getStackInSlot(1), 9, convert);
        world.getChunk(new BlockPos(930, 70, 930));

        ItemEntity drop = new ItemEntity(world, 930, 70, 930, legacy(5));

        NativeLegacyChecks.require(world.addEntity(drop), "Dropped item joins the actual native world");
        identity(drop.getItem(), 5, convert);

        BlockPos position = new BlockPos(930, 70, 932);

        world.setBlockState(position.north(), Blocks.STONE.getDefaultState(), 2);

        ItemFrameEntity frame = new ItemFrameEntity(world, position, Direction.SOUTH);

        frame.setDisplayedItem(legacy(1));
        world.addEntity(frame);
        identity(frame.getDisplayedItem(), 1, convert);

        HandlerTile tile = new HandlerTile();

        tile.setWorld(world);
        tile.handler.setStackInSlot(0, legacy(17));

        Method scan = MigrationEvents.class.getDeclaredMethod("containers", TileEntity.class);

        scan.setAccessible(true);
        scan.invoke(null, tile);
        identity(tile.handler.getStackInSlot(0), 17, convert);

        CompoundNBT once = tile.handler.serializeNBT();

        scan.invoke(null, tile);
        NativeLegacyChecks.require(once.equals(tile.handler.serializeNBT()), "Shared handler faces convert only once");

        ItemStack crate = new ItemStack(Blocks.CHEST);
        CompoundNBT tag = new CompoundNBT();

        tag.putBoolean("Sealed", true);

        ListNBT contents = new ListNBT();

        contents.add(legacy(7).write(new CompoundNBT()));
        tag.put("Items", contents);
        crate.setTag(tag);

        ItemStack migrated = MigrationEvents.convert(crate, world);
        CompoundNBT recovered = migrated.getTag().getList("Items", 10).getCompound(0);

        identity(ItemStack.read(recovered), 7, convert);
        NativeLegacyChecks.require(migrated.getItem() == crate.getItem() && migrated.getTag().getBoolean("Sealed"),
                "Sealed container identity and custom data");
        NativeLegacyChecks.require(MigrationEvents.convert(migrated, world) == migrated,
                "Live nested inventory is idempotent");

        CraftingInventory grid = new CraftingInventory(new Container(null, 0) {
            @Override
            public boolean canInteractWith(PlayerEntity ignored) {
                return true;
            }
        }, 3, 3);

        grid.setInventorySlotContents(0, legacy(1));

        LegacyPieceRecipe recipe = new LegacyPieceRecipe(new ResourceLocation("skysbuildingpieces:bridge_test"));

        NativeLegacyChecks.require(recipe.matches(grid, world),
                "Explicit bridge recipe works independently of replacement");
        identity(recipe.getCraftingResult(grid), 1, true);
        drop.remove();
        frame.remove();
        world.removeBlock(position.north(), false);
        player.inventory.clear();
        player.getInventoryEnderChest().clear();
    }

    private static void newlyPlaced(ServerWorld world, boolean convert) {
        BlockPos position = new BlockPos(934, 70, 932);
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("buildingbricks:rock_step"));
        BlockState old = block.getDefaultState().with((IntegerProperty) block.getStateContainer().getProperty("meta"),
                11);

        world.setBlockState(position, old, 2);

        TileEntity tile = world.getTileEntity(position);
        CompoundNBT tag = tile.write(new CompoundNBT());

        tag.putString("material", "minecraft:stone");
        tag.putString("custom", "Keep the old tile in coverage");
        tile.read(tag);
        MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.getBlockSnapshot(world, position),
                Blocks.STONE.getDefaultState(), FakePlayerFactory.getMinecraft(world)));
        NativeLegacyChecks.require(world.getBlockState(position).getBlock().getRegistryName().getNamespace()
                .equals(convert ? "skysbuildingpieces" : "buildingbricks"), "New placement replacement policy");
        NativeLegacyChecks.require((world.getTileEntity(position) == null) == convert,
                "Remove the material tile only after successful conversion");
        world.removeBlock(position, false);
    }

    private static void unknownMaterial(ServerWorld world) {
        ItemStack unknown = legacy(3);

        unknown.getTag().putString("material", "missing:unknown");

        CompoundNBT before = unknown.write(new CompoundNBT());
        ItemStack result = MigrationEvents.convert(unknown, world);

        NativeLegacyChecks.require(result.write(new CompoundNBT()).equals(before),
                "Installed legacy mod retains unsupported materials");

        // Horizontal soil recovery belongs to Grass Slabs and must stay outside this bridge.
        CompoundNBT soil = NativeLegacyChecks.stack("minecraft:dirt", 7);

        soil.putString("id", "buildingbricks:dirt_slab");
        before = soil.copy();
        LegacyBridge.prepareStack(soil);
        NativeLegacyChecks.require(soil.equals(before), "Core must not take ownership of horizontal soil slabs");
    }

    public static final class HandlerTile extends TileEntity {
        final ItemStackHandler handler = new ItemStackHandler(2);
        private final LazyOptional<ItemStackHandler> optional = LazyOptional.of(() -> handler);

        HandlerTile() {
            super(TileEntityType.CHEST);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? optional.cast()
                    : super.getCapability(capability, side);
        }
    }
}
