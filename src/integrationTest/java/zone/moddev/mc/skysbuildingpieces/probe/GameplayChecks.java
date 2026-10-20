package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Real world placement and collision checks, isolated from the saved palette grid. */
public final class GameplayChecks {
    private static EnumActionResult use(ItemStack stack, EntityPlayerMP player, WorldServer world, BlockPos pos,
            EnumHand hand, EnumFacing face, float hitX, float hitY, float hitZ) {
        player.setHeldItem(hand, stack);
        return stack.getItem().onItemUse(player, world, pos, hand, face, hitX, hitY, hitZ);
    }

    public static void run(WorldServer world) {
        EntityPlayerMP player = FakePlayerFactory.getMinecraft(world);

        player.capabilities.isCreativeMode = false;

        BlockPos position = new BlockPos(-500, 90, -500);

        world.setBlockToAir(position.up());
        world.setBlockToAir(position);

        int placed = 0;

        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            ItemStack item = new ItemStack(Item.getItemFromBlock(pieceBlock), 2, 0);

            world.setBlockToAir(position);
            player.setSneaking(false);

            EnumActionResult result = use(item, player, world, position, EnumHand.MAIN_HAND, EnumFacing.UP, .25f, .1f,
                    .25f);

            require(result == EnumActionResult.SUCCESS && item.getCount() == 1,
                    "own placement and consumption " + pieceBlock.getRegistryName());

            IBlockState state = world.getBlockState(position);

            if (pieceBlock.palette.shape == Shape.VERTICAL_SLAB
                    && Pieces.state(pieceBlock.palette.materials.get(0), Shape.SLAB, 1) != null) {
                require(state.equals(Pieces.state(pieceBlock.palette.materials.get(0), Shape.SLAB, 1)),
                        "saved vertical item uses regular slab placement");
                state = pieceBlock.getStateFromMeta(0);
                world.setBlockState(position, state, 2);
            } else {
                require(state.getBlock() == pieceBlock, "own placement bypasses legacy catalogue");
            }

            require(world.getTileEntity(position) == null, "pieces never need tiles");

            List<AxisAlignedBB> boxes = pieceBlock.boxes(pieceBlock.getActualState(state, world, position));

            require(!boxes.isEmpty(), "collision boxes");

            Catalogue.MaterialDef definition = pieceBlock.definition(state);
            IBlockState nativeFull = Pieces.nativeState(definition, "full");

            require(pieceBlock.getBlockHardness(state, world,
                    position) == (definition.nativeProperties()
                            ? nativeFull.getBlock().getBlockHardness(nativeFull, world, position)
                            : definition.hardness()),
                    "material hardness");
            require(pieceBlock.getSoundType(state, world, position, player) != null, "material sound");

            for (EnumFacing face : EnumFacing.values()) {
                require(pieceBlock.shouldSideBeRendered(state, world, position, face), "partial face remains visible");
            }

            placed++;
        }

        // Two complementary glass slabs become glass, and consume just one item.
        world.setBlockState(position, Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);

        ItemStack slab = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);

        require(use(slab, player, world, position, EnumHand.MAIN_HAND, EnumFacing.UP, .25f, .75f,
                .25f) == EnumActionResult.SUCCESS, "matching slabs combine");
        require(world.getBlockState(position).getBlock() == Blocks.GLASS && slab.getCount() == 1,
                "normalized full block without duplication");
        world.setBlockState(position, Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);
        slab = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);
        require(use(slab, player, world, position, EnumHand.MAIN_HAND, EnumFacing.DOWN, .25f, .5f,
                .25f) == EnumActionResult.SUCCESS && world.getBlockState(position).getBlock() == Blocks.GLASS
                && slab.getCount() == 1, "top slab combines on its lower face");

        for (int orientation = 0; orientation < 4; orientation++) {
            world.setBlockState(position, Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, orientation), 2);

            ItemStack vertical = Pieces.stack(Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, 0), 2);
            EnumFacing face = new EnumFacing[] { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
                    EnumFacing.WEST }[orientation];
            float hitX = face.getAxis() == EnumFacing.Axis.X ? .5f : .25f;
            float hitZ = face.getAxis() == EnumFacing.Axis.Z ? .5f : .25f;

            require(use(vertical, player, world, position, EnumHand.MAIN_HAND, face, hitX, .25f,
                    hitZ) == EnumActionResult.SUCCESS && world.getBlockState(position).getBlock() == Blocks.GLASS
                    && vertical.getCount() == 1, "vertical slab internal boundary " + orientation);
        }
        // Unlike materials must not replace an occupied piece.
        for (int orientation = 0; orientation < 4; orientation++) {
            IBlockState dirt = Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, orientation);

            world.setBlockState(position, dirt, 2);
            world.setBlockState(position.north(), Blocks.SNOW_LAYER.getDefaultState(), 2);
            require(dirt.getBlock().getActualState(dirt, world, position).getValue(BlockGrass.SNOWY),
                    "vertical dirt snow cap " + orientation);
            world.setBlockToAir(position.north());

            ItemStack vertical = Pieces.stack(dirt, 2);
            EnumFacing face = new EnumFacing[] { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
                    EnumFacing.WEST }[orientation];
            float hitX = face.getAxis() == EnumFacing.Axis.X ? .5f : .25f;
            float hitZ = face.getAxis() == EnumFacing.Axis.Z ? .5f : .25f;

            require(use(vertical, player, world, position, EnumHand.MAIN_HAND, face, hitX, .25f,
                    hitZ) == EnumActionResult.SUCCESS
                    && world.getBlockState(position).equals(Blocks.DIRT.getDefaultState()) && vertical.getCount() == 1,
                    "vertical dirt full-block combination " + orientation);
        }

        IBlockState original = Pieces.state("minecraft:glass", Shape.SLAB, 1);

        world.setBlockState(position, original, 2);
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);

        ItemStack other = Pieces.stack(Pieces.state("minecraft:ice", Shape.SLAB, 0), 2);

        use(other, player, world, position, EnumHand.MAIN_HAND, EnumFacing.UP, .25f, .75f, .25f);
        require(world.getBlockState(position).equals(original) && other.getCount() == 2,
                "unlike combination rejected unchanged");
        slabPlacement(world, position, player);
        nativeSlabs(world, position, player);
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);

        // Grass shapes decay to dirt without changing their octant geometry.
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                IBlockState grass = Pieces.state("minecraft:grass", shape, orientation);

                world.setBlockState(position, grass, 2);
                grass.getBlock().updateTick(world, position, grass, new Random(0));
                require(world.getBlockState(position).equals(Pieces.state("minecraft:dirt", shape, orientation)),
                        "covered grass decay preserves geometry");
            }
        }

        world.setBlockToAir(position.up());
        world.setBlockState(position.north(), Blocks.SNOW_LAYER.getDefaultState(), 2);

        IBlockState grass = Pieces.state("minecraft:grass", Shape.CORNER, 0);

        world.setBlockState(position, grass, 2);
        require(grass.getBlock().getActualState(grass, world, position).getValue(BlockGrass.SNOWY),
                "adjacent snow visual");
        world.setBlockToAir(position.north());
        require(!grass.getBlock().getActualState(grass, world, position).getValue(BlockGrass.SNOWY),
                "snow visual clears");
        world.setBlockToAir(position);
        System.out.println("BUILDING_PIECES_GAMEPLAY_PASS " + placed + " palettes");
        RecipeChecks.run(world);
        materialPhysics(world, position, player);
        PresentationChecks.run(world);
    }

    private static void slabPlacement(WorldServer world, BlockPos position, EntityPlayerMP player) {
        int checked = 0;

        // Real ItemBlock calls against a solid neighbour: centre plus four edges, all six faces, every custom
        // horizontal slab material and both sneak modes.
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape == Shape.SLAB) {
                for (int slot = 0; slot < block.palette.materials.size(); slot++) {
                    for (EnumFacing face : EnumFacing.values()) {
                        for (int click = 0; click < 5; click++) {
                            for (boolean sneak : new boolean[] { false, true }) {
                                clearAround(world, position);
                                player.setSneaking(sneak);

                                BlockPos anchor = position.offset(face.getOpposite());

                                world.setBlockState(anchor, Blocks.STONE.getDefaultState(), 2);

                                float[] edge = { .5f, .1f, .9f, .5f, .5f };
                                float[] height = { .5f, .5f, .5f, .1f, .9f };

                                float hitX;
                                float hitY;
                                float hitZ;

                                EnumFacing expected;

                                if (face.getAxis() == EnumFacing.Axis.Y) {
                                    hitX = edge[click];
                                    hitZ = height[click];
                                    hitY = face == EnumFacing.UP ? 1 : 0;
                                    expected = new EnumFacing[] { face.getOpposite(), EnumFacing.WEST, EnumFacing.EAST,
                                            EnumFacing.NORTH, EnumFacing.SOUTH }[click];
                                } else if (face.getAxis() == EnumFacing.Axis.X) {
                                    hitX = face == EnumFacing.EAST ? 1 : 0;
                                    hitY = height[click];
                                    hitZ = edge[click];
                                    expected = new EnumFacing[] { face.getOpposite(), EnumFacing.NORTH,
                                            EnumFacing.SOUTH, EnumFacing.DOWN, EnumFacing.UP }[click];
                                } else {
                                    hitX = edge[click];
                                    hitY = height[click];
                                    hitZ = face == EnumFacing.SOUTH ? 1 : 0;
                                    expected = new EnumFacing[] { face.getOpposite(), EnumFacing.WEST, EnumFacing.EAST,
                                            EnumFacing.DOWN, EnumFacing.UP }[click];
                                }

                                ItemStack stack = new ItemStack(Item.getItemFromBlock(block), 2, slot * 2);

                                require(use(stack, player, world, anchor, EnumHand.MAIN_HAND, face, hitX, hitY,
                                        hitZ) == EnumActionResult.SUCCESS,
                                        "slab face/edge placement " + face + " " + click);
                                require(world.getBlockState(position)
                                        .equals(Pieces.state(block.palette.materials.get(slot), slabShape(expected),
                                                slabOrientation(expected))),
                                        "slab selects correct half " + face + " " + click);
                                require(stack.getCount() == 1 && world.getBlockState(anchor).getBlock() == Blocks.STONE,
                                        "one item consumed; supporting block intact");
                                checked++;
                            }
                        }
                    }
                }
            }
        }

        player.setSneaking(false);

        // A horizontal-slab item also fills the missing half of a vertical slab.
        for (Shape held : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB }) {
            for (EnumFacing occupied : EnumFacing.values()) {
                clearAround(world, position);
                world.setBlockState(position,
                        Pieces.state("minecraft:glass", slabShape(occupied), slabOrientation(occupied)), 2);

                ItemStack stack = Pieces.stack(Pieces.state("minecraft:glass", held, 0), 2);

                require(use(stack, player, world, position, EnumHand.MAIN_HAND, occupied.getOpposite(), .5f, .5f,
                        .5f) == EnumActionResult.SUCCESS, "cross-orientation slab combination");
                require(world.getBlockState(position).getBlock() == Blocks.GLASS && stack.getCount() == 1,
                        "six-way combination normalizes exactly once");
            }
        }

        // If the clicked full block cannot combine, use the neighbour's local coordinates when filling its top slab
        // from below.
        clearAround(world, position);
        world.setBlockState(position, Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(position.up(), Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);

        ItemStack stack = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);

        require(use(stack, player, world, position, EnumHand.MAIN_HAND, EnumFacing.UP, .5f, 1,
                .5f) == EnumActionResult.SUCCESS && world.getBlockState(position.up()).getBlock() == Blocks.GLASS
                && stack.getCount() == 1, "adjacent slab combination uses destination coordinates");
        clearAround(world, position);
        world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);
        stack = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);
        player.capabilities.allowEdit = false;

        try {
            require(use(stack, player, world, position.down(), EnumHand.MAIN_HAND, EnumFacing.UP, .5f, 1,
                    .5f) == EnumActionResult.FAIL && world.isAirBlock(position) && stack.getCount() == 2,
                    "denied placement does not change the world or consume a slab");
        } finally {
            player.capabilities.allowEdit = true;
        }

        net.minecraft.entity.item.EntityArmorStand stand = new net.minecraft.entity.item.EntityArmorStand(world);

        stand.setPosition(position.getX() + .25, position.getY(), position.getZ() + .5);
        require(world.spawnEntity(stand), "collision fixture joins world");

        try {
            require(use(stack, player, world, position.down(), EnumHand.MAIN_HAND, EnumFacing.UP, .1f, 1,
                    .5f) == EnumActionResult.FAIL && world.isAirBlock(position) && stack.getCount() == 2,
                    "occupied selected half rejects placement without consumption");
            stand.setPosition(position.getX() + .75, position.getY(), position.getZ() + .5);
            require(use(stack, player, world, position.down(), EnumHand.MAIN_HAND, EnumFacing.UP, .1f, 1,
                    .5f) == EnumActionResult.SUCCESS
                    && world.getBlockState(position).equals(Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, 1))
                    && stack.getCount() == 1, "empty selected half permits placement next to an entity");
        } finally {
            stand.setDead();
            world.removeEntity(stand);
        }

        clearAround(world, position);
        System.out.println(
                "BUILDING_PIECES_SLAB_PLACEMENT_PASS " + checked + " face/edge/sneak cases and 12 combinations");
    }

    private static Shape slabShape(EnumFacing occupied) {
        return occupied.getAxis() == EnumFacing.Axis.Y ? Shape.SLAB : Shape.VERTICAL_SLAB;
    }

    private static int slabOrientation(EnumFacing occupied) {
        return occupied == EnumFacing.UP ? 0 : occupied == EnumFacing.DOWN ? 1 : occupied.getHorizontalIndex();
    }

    private static void clearAround(WorldServer world, BlockPos position) {
        world.setBlockToAir(position);

        for (EnumFacing face : EnumFacing.values()) {
            world.setBlockToAir(position.offset(face));
        }
    }

    private static net.minecraft.inventory.InventoryCrafting grid() {
        return new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, 3, 3);
    }

    private static void materialPhysics(WorldServer world, BlockPos position, EntityPlayerMP player) {
        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            for (int slot = 0; slot < pieceBlock.palette.materials.size(); slot++) {
                for (int orientation = 0; orientation < pieceBlock.palette.shape.states; orientation++) {
                    IBlockState state = pieceBlock
                            .getStateFromMeta(slot * pieceBlock.palette.shape.states + orientation);
                    Catalogue.MaterialDef materialDefinition = pieceBlock.definition(state);

                    world.setBlockState(position, state, 2);
                    require(pieceBlock.getHarvestTool(state) == null || pieceBlock.getHarvestTool(state)
                            .equals(materialDefinition.type.equals("rock") || materialDefinition.type.equals("metal")
                                    ? "pickaxe"
                                    : materialDefinition.type.equals("wood") ? "axe" : "shovel"),
                            "harvest class");

                    IBlockState nativeFull = Pieces.nativeState(materialDefinition, "full");
                    int actualFire = pieceBlock.getFlammability(world, position, EnumFacing.UP);
                    int actualSpread = pieceBlock.getFireSpreadSpeed(world, position, EnumFacing.UP);

                    world.setBlockState(position, nativeFull, 2);
                    require(actualFire == nativeFull.getBlock().getFlammability(world, position, EnumFacing.UP),
                            "native flammability " + materialDefinition.id + " actual=" + actualFire + " expected="
                                    + nativeFull.getBlock().getFlammability(world, position, EnumFacing.UP));
                    require(actualSpread == nativeFull.getBlock().getFireSpreadSpeed(world, position, EnumFacing.UP),
                            "native fire spread");
                    world.setBlockState(position, state, 2);
                    require(ItemStack.areItemStacksEqual(pieceBlock.getPickBlock(state, null, world, position, player),
                            expectedDrop(materialDefinition.id, pieceBlock, state)),
                            "pick identity follows regular slab");

                    try {
                        java.lang.reflect.Method silk;

                        try {
                            silk = PieceBlock.class.getDeclaredMethod("getSilkTouchDrop", IBlockState.class);
                        } catch (NoSuchMethodException packaged) {
                            silk = PieceBlock.class.getDeclaredMethod("func_180643_i", IBlockState.class);
                        }

                        silk.setAccessible(true);
                        require(ItemStack.areItemStacksEqual((ItemStack) silk.invoke(pieceBlock, state),
                                expectedDrop(materialDefinition.id, pieceBlock, state)),
                                "Silk Touch follows regular slab");
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }

                    List<ItemStack> drops = pieceBlock.getDrops(world, position, state, 0);

                    if (materialDefinition.normalDrop().isEmpty()) {
                        require(drops.isEmpty(), "fragile material normal drops");
                    } else {
                        require(drops.size() == 1 && ItemStack.areItemStacksEqual(drops.get(0),
                                expectedDrop(materialDefinition.normalDrop(), pieceBlock, Pieces.state(
                                        materialDefinition.normalDrop(), pieceBlock.palette.shape, orientation))),
                                "material drop and metadata");
                    }
                    if (materialDefinition.soil()) {
                        for (Block plant : new Block[] { Blocks.TALLGRASS, Blocks.SAPLING, Blocks.RED_MUSHROOM }) {
                            IBlockState full = Pieces.nativeState(materialDefinition, "full");
                            boolean nativeSupport = full.getBlock().canSustainPlant(full, world, position,
                                    EnumFacing.UP, (net.minecraftforge.common.IPlantable) plant);

                            require(pieceBlock.canSustainPlant(state, world, position, EnumFacing.UP,
                                    (net.minecraftforge.common.IPlantable) plant) == (nativeSupport
                                            && Geometry.completeTop(pieceBlock.mask(state))),
                                    "native plants need complete upper faces " + materialDefinition.id + " "
                                            + pieceBlock.orientation(state));
                        }
                    }
                }
            }
        }

        world.setBlockToAir(position);
        System.out.println("BUILDING_PIECES_MATERIAL_PHYSICS_PASS");
    }

    private static ItemStack expectedDrop(String material, PieceBlock block, IBlockState state) {
        if (block.palette.shape == Shape.VERTICAL_SLAB) {
            IBlockState horizontal = Pieces.state(material, Shape.SLAB, 1);

            if (horizontal != null) {
                return Pieces.stack(horizontal, 1);
            }

            Item dirt = Item.getByNameOrId("skysgrassslabs:dirt_slab");

            if (material.equals("minecraft:dirt") && dirt != null) {
                return new ItemStack(dirt, 1, 0);
            }
        }

        return Pieces.stack(state, 1);
    }

    private static void nativeSlabs(WorldServer world, BlockPos position, EntityPlayerMP player) {
        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape == Shape.VERTICAL_SLAB) {
                NonNullList<ItemStack> creative = NonNullList.create();

                block.getSubBlocks(net.minecraft.creativetab.CreativeTabs.BUILDING_BLOCKS, creative);
                require(creative.isEmpty(), "vertical slabs absent from creative tab");
            }
        }

        Item dirt = Item.getByNameOrId("skysgrassslabs:dirt_slab");

        if (dirt != null) {
            for (EnumFacing occupied : EnumFacing.HORIZONTALS) {
                clearAround(world, position);
                world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);

                ItemStack stack = new ItemStack(dirt, 2, 0);
                float hitX = occupied == EnumFacing.WEST ? .1f : occupied == EnumFacing.EAST ? .9f : .5f;
                float hitZ = occupied == EnumFacing.NORTH ? .1f : occupied == EnumFacing.SOUTH ? .9f : .5f;

                require(use(stack, player, world, position.down(), EnumHand.MAIN_HAND, EnumFacing.UP, hitX, 1,
                        hitZ) == EnumActionResult.SUCCESS
                        && world.getBlockState(position).equals(
                                Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, occupied.getHorizontalIndex()))
                        && stack.getCount() == 1, "regular dirt slab places vertically");
                ++checked;
            }
        }

        clearAround(world, position);
        world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);

        ItemStack oak = new ItemStack(Blocks.WOODEN_SLAB, 2, 0);

        player.setHeldItem(EnumHand.MAIN_HAND, oak);

        PlacementVeto veto = new PlacementVeto();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);

        try {
            require(oak.onItemUse(player, world, position.down(), EnumHand.MAIN_HAND, EnumFacing.UP, .1f, 1,
                    .5f) != EnumActionResult.SUCCESS && veto.seen && world.isAirBlock(position) && oak.getCount() == 2,
                    "Forge placement protection rolls back the exact native slab placement");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
        }

        clearAround(world, position);
        world.setBlockState(position, Blocks.TRAPDOOR.getDefaultState(), 2);
        player.setHeldItem(EnumHand.MAIN_HAND, oak);
        player.setSneaking(false);
        require(player.interactionManager.processRightClickBlock(player, world, oak, EnumHand.MAIN_HAND, position,
                EnumFacing.UP, .1f, 1, .5f) == EnumActionResult.SUCCESS && world.isAirBlock(position.up())
                && oak.getCount() == 2 && world.getBlockState(position).getValue(BlockTrapDoor.OPEN),
                "block activation takes priority and happens exactly once");
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);

        if (net.minecraftforge.fml.common.Loader.isModLoaded("buildingbricks")) {
            return;
        }
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            if (Pieces.state(material.id, Shape.VERTICAL_SLAB, 0) == null) {
                continue;
            }

            IBlockState nativeSlab = Pieces.nativeState(material, "slab");

            if (nativeSlab == null) {
                continue;
            }
            for (EnumHand hand : EnumHand.values()) {
                for (EnumFacing occupied : EnumFacing.values()) {
                    clearAround(world, position);

                    BlockPos anchor = position.down();

                    world.setBlockState(anchor, Blocks.STONE.getDefaultState(), 2);

                    float hitX = occupied == EnumFacing.WEST ? .1f : occupied == EnumFacing.EAST ? .9f : .5f;
                    float hitZ = occupied == EnumFacing.NORTH ? .1f : occupied == EnumFacing.SOUTH ? .9f : .5f;
                    EnumFacing face = occupied == EnumFacing.UP ? EnumFacing.DOWN : EnumFacing.UP;

                    if (face == EnumFacing.DOWN) {
                        anchor = position.up();
                        world.setBlockState(anchor, Blocks.STONE.getDefaultState(), 2);
                    }

                    ItemStack stack = Pieces.stack(nativeSlab, 3);
                    net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();

                    tag.setString("custom_label", "Preserved slab");
                    stack.setTagCompound(tag);
                    player.setHeldItem(hand, stack);
                    require(stack.onItemUse(player, world, anchor, hand, face, hitX, face == EnumFacing.UP ? 1 : 0,
                            hitZ) == EnumActionResult.SUCCESS, "native slab hook returns success " + material.id);
                    require(world.getBlockState(position)
                            .equals(Pieces.state(material.id, slabShape(occupied), slabOrientation(occupied))),
                            "native slab selects six orientations " + material.id);
                    require(stack.getCount() == 2 && tag.equals(stack.getTagCompound()),
                            "native hook consumes once and preserves NBT");
                    require(stack.onItemUse(player, world, position, hand, occupied.getOpposite(), .5f, .5f,
                            .5f) == EnumActionResult.SUCCESS, "native slab combines");
                    require(world.getBlockState(position).equals(Pieces.nativeState(material, "full"))
                            && stack.getCount() == 1, "native combination makes exact full material");
                    ++checked;
                }
            }
        }

        clearAround(world, position);
        player.setSneaking(false);
        System.out.println("BUILDING_PIECES_NATIVE_SLABS_PASS cases=" + checked + " creative=hidden drops=regular");
    }

    public static final class PlacementVeto {
        boolean seen;

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void place(net.minecraftforge.event.world.BlockEvent.PlaceEvent event) {
            seen = true;
            event.setCanceled(true);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
