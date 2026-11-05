package zone.moddev.mc.skysbuildingpieces.compatprobe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.Identifier;
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

    public LegacyPolicyMod(FMLJavaModLoadingContext context) {
        RegisterEvent.getBus(context.getModBusGroup()).addListener(this::register);
    }

    private void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) {
            for (int index = 0; index < NAMES.length; index++) {
                Identifier id = Identifier.fromNamespaceAndPath("buildingbricks", NAMES[index]);
                BLOCKS[index] = new LegacyBlock(id);
                ForgeRegistries.BLOCKS.register(id, BLOCKS[index]);
            }
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            for (Block block : BLOCKS) {
                Identifier id = ForgeRegistries.BLOCKS.getKey(block);
                ForgeRegistries.ITEMS.register(id,
                        new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
            }
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES)) {
            materialType = new BlockEntityType<>(MaterialTile::new, java.util.Set.of(BLOCKS));
            ForgeRegistries.BLOCK_ENTITY_TYPES.register(Identifier.fromNamespaceAndPath("buildingbricks", "material"), materialType);
        }
    }

    public static final class LegacyBlock extends Block implements EntityBlock {
        private static final IntegerProperty META = IntegerProperty.create("meta", 0, 15);

        LegacyBlock(Identifier id) {
            super(Block.Properties.of().mapColor(MapColor.STONE).setId(ResourceKey.create(Registries.BLOCK, id)));
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
        private static final String[] FIELDS = {"material", "custom", "fixture_note"};
        private final java.util.Map<String, String> retained = new java.util.HashMap<>();

        public MaterialTile(BlockPos position, BlockState state) {
            super(materialType, position, state);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            retained.clear();
            for (String key : FIELDS) {
                input.getString(key).ifPresent(value -> retained.put(key, value));
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            retained.forEach(output::putString);
        }
    }
}
