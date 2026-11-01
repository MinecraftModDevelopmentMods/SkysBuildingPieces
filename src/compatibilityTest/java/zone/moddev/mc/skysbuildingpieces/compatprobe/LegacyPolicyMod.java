package zone.moddev.mc.skysbuildingpieces.compatprobe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** A build-only installed-mod policy fixture; this is not a BuildingBricks port. */
@Mod("buildingbricks")
public final class LegacyPolicyMod {
    private static final String[] NAMES = { "rock_step", "wood_vertical_slab", "glass_slab", "dirt_stairs",
            "wood_corner", "rock_wall", "ice_pane", "dirt_vertical_slab" };
    private static final Block[] BLOCKS = new Block[NAMES.length];
    private static BlockEntityType<MaterialTile> materialType;

    public LegacyPolicyMod() {
        for (int index = 0; index < NAMES.length; index++) {
            BLOCKS[index] = new LegacyBlock().setRegistryName("buildingbricks", NAMES[index]);
        }

        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Block.class, this::blocks);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Item.class, this::items);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(BlockEntityType.class, this::tiles);
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

    private void tiles(RegistryEvent.Register<BlockEntityType<?>> event) {
        materialType = BlockEntityType.Builder.of(MaterialTile::new, BLOCKS).build(null);
        materialType.setRegistryName("buildingbricks", "material");
        event.getRegistry().register(materialType);
    }

    public static final class LegacyBlock extends Block implements EntityBlock {
        private static final IntegerProperty META = IntegerProperty.create("meta", 0, 15);

        LegacyBlock() {
            super(Block.Properties.of(Material.STONE));
            registerDefaultState(stateDefinition.any().setValue(META, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(META);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
            return new MaterialTile(position, state);
        }
    }

    public static final class MaterialTile extends BlockEntity {
        private CompoundTag retained = new CompoundTag();

        public MaterialTile(BlockPos position, BlockState state) {
            super(materialType, position, state);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            retained = tag.copy();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.merge(retained);
            return super.save(tag);
        }
    }
}
