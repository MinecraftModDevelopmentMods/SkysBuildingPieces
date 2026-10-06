package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Loads only indexed existing chunks in a disposable full-world copy. */
public final class WorldAuditProbe {
    private final net.minecraft.server.MinecraftServer server;
    private final List<int[]> chunks=new ArrayList<int[]>();
    private int next;
    private final boolean reload;
    public WorldAuditProbe(WorldServer world,boolean reload) throws IOException {
        this.server=world.getMinecraftServer();this.reload=reload;
        int ids=0,highest=0;for(net.minecraft.block.Block block:net.minecraft.block.Block.REGISTRY){ids++;highest=Math.max(highest,net.minecraft.block.Block.getIdFromBlock(block));}
        System.out.println("BUILDING_PIECES_WORLD_REGISTRY registered="+ids+" highest="+highest+" free="+(4096-ids));
        Path root=world.getSaveHandler().getWorldDirectory().toPath();
        try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
            Iterator<Path> files=paths.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).sorted().iterator();
            while(files.hasNext()) {
                Path p=files.next();String dimension=root.relativize(p.getParent().getParent()).toString();
                int dim=dimension.isEmpty()?0:dimension.equals("DIM-1")?-1:dimension.equals("DIM1")?1:Integer.MIN_VALUE;
                if(dim==Integer.MIN_VALUE)throw new AssertionError("Unexpected fixture dimension "+dimension);
                String[] coords=p.getFileName().toString().split("\\.");int rx=Integer.parseInt(coords[1]),rz=Integer.parseInt(coords[2]);
                try(RandomAccessFile file=new RandomAccessFile(p.toFile(),"r")){for(int i=0;i<1024;i++)if(file.readInt()!=0)chunks.add(new int[]{dim,rx*32+(i&31),rz*32+(i>>5)});}
            }
        }
        String index=System.getProperty("skysbuildingpieces.auditIndex");
        if(index==null&&Files.isRegularFile(root.getParent().resolve("reload-chunk-index.csv")))index=root.getParent().resolve("reload-chunk-index.csv").toString();
        if(index!=null) {
            Set<String> required=new HashSet<String>(Files.readAllLines(Paths.get(index),java.nio.charset.StandardCharsets.UTF_8));
            chunks.removeIf(p->!required.remove(p[0]+","+p[1]+","+p[2]));
            if(!required.isEmpty())throw new AssertionError("Saved content chunks disappeared: "+required);
        }
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("BUILDING_PIECES_WORLD_AUDIT_INDEX "+chunks.size());
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        try {
            for(int i=0;i<8&&next<chunks.size();i++) {
                int[] point=chunks.get(next++);WorldServer world=server.worldServerForDimension(point[0]);
                Chunk chunk=world.getChunkFromChunkCoords(point[1],point[2]);world.getChunkProvider().unload(chunk);
            }
            if(next%2048==0)System.out.println("BUILDING_PIECES_WORLD_AUDIT_PROGRESS "+next+" / "+chunks.size());
            if(next==chunks.size()) {
                WorldServer world=server.worldServerForDimension(0);long blocks=0,items=0;
                if(LegacyBridge.active()) {
                    LegacyBridge.report(world);PiecesWorldState state=PiecesWorldState.get(world);blocks=state.blocks;items=state.items;
                    if(blocks!=6072)throw new AssertionError("Qualified vanilla conversion count: "+blocks+" instead of 6072");
                    if(items!=1170)throw new AssertionError("Qualified encountered inventory count: "+items+" instead of 1170");
                    if(net.minecraftforge.fml.common.Loader.isModLoaded("skysbuildingpiecesbop")) {
                        PiecesWorldState.Totals bop=state.modules.get("biomesoplenty");
                        if(bop==null||bop.blocks!=601||bop.items!=52)throw new AssertionError("Qualified BOP totals: "+(bop==null?"missing":bop.blocks+" / "+bop.items));
                    }
                }else {
                    File root=world.getSaveHandler().getWorldDirectory();
                    if(new File(root,"data/skysbuildingpieces_world_state.dat").exists()||new File(root,"serverconfig/skysbuildingpieces-migration-report.txt").exists())throw new AssertionError("Default coexistence wrote migration state");
                }
                Properties result=new Properties();result.setProperty("indexed_chunks",Integer.toString(chunks.size()));result.setProperty("blocks",Long.toString(blocks));result.setProperty("items",Long.toString(items));result.setProperty("reload",Boolean.toString(reload));
                try(OutputStream out=new FileOutputStream(new File(world.getSaveHandler().getWorldDirectory(),reload?"building-pieces-reload-pass.properties":"building-pieces-fresh-pass.properties"))){result.store(out,"Disposable world qualification");}
                System.out.println("BUILDING_PIECES_WORLD_AUDIT_PASS blocks="+blocks+" items="+items+" reload="+reload);
                MinecraftForge.EVENT_BUS.unregister(this);server.initiateShutdown();
            }
        }catch(Throwable failure){failure.printStackTrace();net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(2,true);}
    }
}
