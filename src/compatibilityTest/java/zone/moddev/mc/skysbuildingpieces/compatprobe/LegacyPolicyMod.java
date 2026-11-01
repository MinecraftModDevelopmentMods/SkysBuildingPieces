package zone.moddev.mc.skysbuildingpieces.compatprobe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** A build-only installed-mod policy fixture; this is not a BuildingBricks port. */
@Mod("buildingbricks")
public final class LegacyPolicyMod {
    private static final String[] NAMES = { "rock_step", "wood_vertical_slab", "glass_slab", "dirt_stairs",
            "wood_corner", "rock_wall", "ice_pane", "dirt_vertical_slab" };
    private static final Block[] BLOCKS = new Block[NAMES.length];
    private static TileEntityType<MaterialTile> materialType;

    public LegacyPolicyMod() {
        for (int index = 0; index < NAMES.length; index++) {
            BLOCKS[index] = new LegacyBlock().setRegistryName("buildingbricks", NAMES[index]);
        }

        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Block.class, this::blocks);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Item.class, this::items);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(TileEntityType.class, this::tiles);
    }

    private void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(BLOCKS);
    }

    private void items(RegistryEvent.Register<Item> event) {
        for (Block block : BLOCKS) {
            event.getRegistry()
                    .register(new BlockItem(block, new Item.Properties()).setRegistryName(block.getRegistryName()));
        }
    }

    private void tiles(RegistryEvent.Register<TileEntityType<?>> event) {
        materialType = TileEntityType.Builder.of(MaterialTile::new, BLOCKS).build(null);
        materialType.setRegistryName("buildingbricks", "material");
        event.getRegistry().register(materialType);
    }

    public static final class LegacyBlock extends Block {
        private static final IntegerProperty META = IntegerProperty.create("meta", 0, 15);

        LegacyBlock() {
            super(Block.Properties.of(Material.STONE));
            registerDefaultState(stateDefinition.any().setValue(META, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
            builder.add(META);
        }

        @Override
        public boolean hasTileEntity(BlockState state) {
            return true;
        }

        @Override
        public TileEntity createTileEntity(BlockState state, IBlockReader world) {
            return new MaterialTile();
        }
    }

    public static final class MaterialTile extends TileEntity {
        private CompoundNBT retained = new CompoundNBT();

        public MaterialTile() {
            super(materialType);
        }

        @Override
        public void load(BlockState state, CompoundNBT tag) {
            super.load(state, tag);
            retained = tag.copy();
        }

        @Override
        public CompoundNBT save(CompoundNBT tag) {
            tag.merge(retained);
            return super.save(tag);
        }
    }
}
