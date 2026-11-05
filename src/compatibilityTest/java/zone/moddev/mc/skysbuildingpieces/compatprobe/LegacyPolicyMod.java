package zone.moddev.mc.skysbuildingpieces.compatprobe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.fml.common.Mod;

/** A build-only installed-mod policy fixture; this is not a BuildingBricks port. */
@Mod("buildingbricks")
public final class LegacyPolicyMod {
    private static final String[] NAMES = { "rock_step", "wood_vertical_slab", "glass_slab", "dirt_stairs",
            "wood_corner", "rock_wall", "ice_pane", "dirt_vertical_slab" };
    private static final Block[] BLOCKS = new Block[NAMES.length];
    private static BlockEntityType<MaterialTile> materialType;

    public LegacyPolicyMod(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(this::register);
    }

    private void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)) {
            for (int index = 0; index < NAMES.length; index++) {
                BLOCKS[index] = new LegacyBlock();
                net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation("buildingbricks", NAMES[index]), BLOCKS[index]);
            }
        } else if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)) {
            for (Block block : BLOCKS) {
                net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK.getKey(block),
                        new BlockItem(block, new Item.Properties()));
            }
        } else if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE)) {
            materialType = BlockEntityType.Builder.of(MaterialTile::new, BLOCKS).build(null);
            net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    new ResourceLocation("buildingbricks", "material"), materialType);
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
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            retained = tag.copy();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            tag.merge(retained);
            super.saveAdditional(tag, registries);
        }
    }
}
