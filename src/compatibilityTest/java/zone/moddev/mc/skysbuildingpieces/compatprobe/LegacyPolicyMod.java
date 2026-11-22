package zone.moddev.mc.skysbuildingpieces.compatprobe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
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
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::register);
    }

    private void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) {
            for (int index = 0; index < NAMES.length; index++) {
                BLOCKS[index] = new LegacyBlock();
                ForgeRegistries.BLOCKS.register(new ResourceLocation("buildingbricks", NAMES[index]), BLOCKS[index]);
            }
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            for (Block block : BLOCKS) {
                ForgeRegistries.ITEMS.register(ForgeRegistries.BLOCKS.getKey(block),
                        new BlockItem(block, new Item.Properties()));
            }
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES)) {
            materialType = BlockEntityType.Builder.of(MaterialTile::new, BLOCKS).build(null);
            ForgeRegistries.BLOCK_ENTITY_TYPES.register(new ResourceLocation("buildingbricks", "material"), materialType);
        }
    }

    public static final class LegacyBlock extends Block implements EntityBlock {
        private static final IntegerProperty META = IntegerProperty.create("meta", 0, 15);

        LegacyBlock() {
            super(Block.Properties.of().mapColor(MapColor.STONE));
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
        protected void saveAdditional(CompoundTag tag) {
            tag.merge(retained);
            super.saveAdditional(tag);
        }
    }
}
