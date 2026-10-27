package zone.moddev.mc.skysbuildingpieces.mineralogytest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

@Mod(modid = "buildingpiecesprobe", name = "Mineralogy Pieces Runtime Probe", version = "1",
        dependencies = "required-after:skysbuildingpieces;required-after:mineralogy;after:skysterrainsmoother")
public final class RuntimeProbe {
    public static volatile boolean complete;
    private MinecraftServer server;
    private boolean ran;
    @SidedProxy(clientSide = "zone.moddev.mc.skysbuildingpieces.mineralogytest.ClientProbe",
            serverSide = "zone.moddev.mc.skysbuildingpieces.mineralogytest.ProbeProxy")
    public static ProbeProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        proxy.init();
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (ran || server == null || event.phase != TickEvent.Phase.END) return;
        ran = true;
        try {
            WorldServer world = server.worldServerForDimension(0);
            String phase = System.getProperty("skysbuildingpieces.integrationPhase");
            require(phase != null, "Missing test phase");
            LegacyInventoryChecks.run(world, phase);
            zone.moddev.mc.skysbuildingpieces.probe.PathChecks.run(world);
            int states = 0;
            Catalogue.Module module = Catalogue.INSTANCE.modules.get("mineralogy");
            require(module.palettes.size() == 35, "Block budget changed");
            int registeredBlocks = 0;
            for (net.minecraft.block.Block block : net.minecraft.block.Block.REGISTRY) {
                if (block.getRegistryName().toString().startsWith("skysbuildingpieces:mineralogy_")) {
                    registeredBlocks++;
                }
            }
            require(registeredBlocks == 35, "The actual block registry exceeds the 35-ID budget");
            String generation = net.minecraftforge.fml.common.Loader.isModLoaded("skysterrainsmoother")
                    ? SmoothingChecks.generated(world) : "";

            for (Catalogue.MaterialDef material : module.materials.values()) {
                IBlockState bottom = Pieces.state(material.id, Shape.SLAB, 1);
                IBlockState top = Pieces.state(material.id, Shape.SLAB, 0);
                if (bottom == null) {
                    require(top == null, "Disabled native slab has only one half");
                    continue;
                }
                require(facing(bottom) == EnumFacing.UP && facing(top) == EnumFacing.DOWN,
                        "NATIVE_SLAB_ORIENTATION " + material.id + ": expected UP/DOWN, got "
                                + facing(bottom) + "/" + facing(top));
                ItemStack slab = Pieces.stack(bottom, 1);
                require(slab.getItemDamage() == 0, "NATIVE_SLAB_ITEM_METADATA " + material.id);
            }

            for (Catalogue.Palette palette : module.palettes) {
                for (String material : palette.materials) {
                    for (int orientation = 0; orientation < palette.shape.states; orientation++) {
                        IBlockState expected = Pieces.state(material, palette.shape, orientation);
                        BlockPos position = new BlockPos(states & 15, 100 + (states >> 8), (states >> 4) & 15);
                        if (phase.contains("reload") || Boolean.getBoolean("mineralogycore.upgrade")) {
                            require(world.getBlockState(position).equals(expected), "Reload state changed: " + position);
                        } else {
                            require(world.setBlockState(position, expected, 2), "Placement failed: " + position);
                        }
                        require(!expected.getBlock().hasTileEntity(expected), "A piece requires a tile entity");
                        states++;
                    }
                }
            }
            require(states == 540, "Incomplete orientation coverage");

            if (!Boolean.getBoolean("mineralogycore.generationOnly")) {
                zone.moddev.mc.skysbuildingpieces.probe.GameplayChecks.run(world);
            }
            int smoother = net.minecraftforge.fml.common.Loader.isModLoaded("skysterrainsmoother")
                    ? SmoothingChecks.run(world) : 0;
            if (smoother > 0 && Boolean.getBoolean("mineralogycore.generationOrder") && !phase.contains("reload")) {
                OrderChecks.run(world);
            }
            File marker = new File(world.getSaveHandler().getWorldDirectory(), "skysbuildingpieces-integration.properties");
            Properties values = new Properties();
            if (marker.isFile()) {
                try (FileInputStream input = new FileInputStream(marker)) { values.load(input); }
            }
            if (phase.contains("reload")) {
                require(generation.equals(values.getProperty("generation", "")), "Generated pieces changed on reload");
            }
            values.setProperty("generation", generation);
            values.setProperty(phase.replace('-', '_') + "_complete", "true");
            values.setProperty("piece_checks", Integer.toString(states));
            values.setProperty("smoothing_checks", Integer.toString(smoother));
            try (FileOutputStream output = new FileOutputStream(marker)) {
                values.store(output, "Disposable Mineralogy piece checks");
            }
            System.out.println("MINERALOGY_PIECES_PASS phase=" + phase + " palettes=35 states=540 finer_mappings=" + smoother);
            complete = true;
            if (server.isDedicatedServer()) server.initiateShutdown();
        } catch (Throwable failure) {
            failure.printStackTrace();
            net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(2, true);
        }
    }

    private static EnumFacing facing(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property.getName().equals("facing")) return (EnumFacing) state.getProperties().get(property);
        }
        throw new AssertionError("Native slab has no facing property");
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
