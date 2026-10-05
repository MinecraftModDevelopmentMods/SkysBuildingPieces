package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Runs before block storage and item/TE deserialization. Fail closed on unknown absent content. */
public final class LegacyBridge {
    public static volatile boolean ready;
    private static final Map<Integer,String> savedIds=new HashMap<Integer,String>();
    private static final Map<String,Long> unresolved=new TreeMap<String,Long>();
    private static File worldDirectory;
    private static final Map<String,java.util.concurrent.atomic.AtomicLong> pendingItems=new java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicLong>();
    private LegacyBridge() { }
    public static boolean active() { return ready && LegacyMapping.enabled(Loader.isModLoaded("buildingbricks"),SkysBuildingPieces.forceReplace); }
    public static void start(MinecraftServer server) {
        stop();
        worldDirectory=server.isDedicatedServer() ? server.getFile(server.getFolderName()) : new File(server.getDataDirectory(),"saves/"+server.getFolderName());
        File level=new File(worldDirectory,"level.dat");
        if(level.isFile())try {
            NBTTagCompound root;
            try(InputStream in=new FileInputStream(level)){root=CompressedStreamTools.readCompressed(in);}
            NBTTagCompound registry=root.getCompoundTag("FML").getCompoundTag("Registries").getCompoundTag("minecraft:blocks");
            NBTTagList ids=registry.getTagList("ids",10);
            for(int i=0;i<ids.tagCount();i++) {NBTTagCompound id=ids.getCompoundTagAt(i);String name=id.getString("K");if(LegacyMapping.owned(name))savedIds.put(id.getInteger("V"),name);}
            File sidecar=new File(worldDirectory,"data/skysbuildingpieces_legacy_registry.dat");
            if(sidecar.isFile()) {NBTTagList prior;
                try(InputStream in=new FileInputStream(sidecar)){prior=CompressedStreamTools.readCompressed(in).getCompoundTag("Registries").getCompoundTag("minecraft:blocks").getTagList("ids",10);}
                for(int i=0;i<prior.tagCount();i++){NBTTagCompound id=prior.getCompoundTagAt(i);if(LegacyMapping.owned(id.getString("K")))savedIds.putIfAbsent(id.getInteger("V"),id.getString("K"));}}
            if(!Loader.isModLoaded("buildingbricks") && !savedIds.isEmpty())LegacyPreflight.validate(worldDirectory,savedIds);
            // An unsafe upgrade must not write even a migration sidecar.
            if(active() && !savedIds.isEmpty() && !sidecar.isFile()) {
                sidecar.getParentFile().mkdirs();
                try(OutputStream out=new FileOutputStream(sidecar)){CompressedStreamTools.writeCompressed(root.getCompoundTag("FML"),out);}
            }
        }catch(IOException e) { throw new RecoveryAbort("Cannot read the legacy registry snapshot",e); }
    }
    public static synchronized void stop() { savedIds.clear();unresolved.clear();worldDirectory=null;pendingItems.clear(); }
    public static boolean hasPendingReport() { synchronized(unresolved){return pendingItems.values().stream().anyMatch(n->n.get()!=0) || !unresolved.isEmpty();} }
    public static void prepareStack(NBTTagCompound stack) {
        if(!active())return;
        List<StackChange> changes=new ArrayList<StackChange>();
        collectStack(stack,changes);
        // Resolve every nested target before changing any part of the stack.
        for(StackChange change:changes) {
            change.stack.setString("id",change.output.getItem().getRegistryName().toString());
            change.stack.setShort("Damage",(short)change.output.getMetadata());
            if(worldDirectory!=null)pendingItems.computeIfAbsent(change.module,k->new java.util.concurrent.atomic.AtomicLong()).addAndGet(change.stack.getByte("Count")&255);
        }
    }
    private static void collectStack(NBTTagCompound stack,List<StackChange> changes) {
        if(stack.hasKey("id",8)&&stack.hasKey("Count",99))collectIdentity(stack,changes);
        collectInventories(stack.getTag("tag"),changes);
        collectInventories(stack.getTag("ForgeCaps"),changes);
    }
    private static void collectInventories(NBTBase value,List<StackChange> changes) {
        if(value instanceof NBTTagCompound) {
            NBTTagCompound compound=(NBTTagCompound)value;
            for(String key:compound.getKeySet()) {
                NBTBase child=compound.getTag(key);
                if(key.equals("Items")&&child instanceof NBTTagList&&((NBTTagList)child).getTagType()==10) {
                    NBTTagList items=(NBTTagList)child;
                    for(int i=0;i<items.tagCount();i++)collectStack(items.getCompoundTagAt(i),changes);
                } else collectInventories(child,changes);
            }
        } else if(value instanceof NBTTagList) {
            NBTTagList list=(NBTTagList)value;
            for(int i=0;i<list.tagCount();i++)collectInventories(list.get(i),changes);
        }
    }
    private static void collectIdentity(NBTTagCompound stack,List<StackChange> changes) {
        String registry=stack.getString("id");if(!LegacyMapping.owned(registry))return;
        NBTTagCompound tag=stack.getCompoundTag("tag");
        String material=LegacyMapping.material(tag.getString("material"));
        // Item damage is a dynamic material index, never a placement orientation.
        Shape shape=LegacyMapping.shape(registry,0);
        IBlockState target=resolve(registry,material,shape,shape==Shape.SLAB ? 1 : 0);
        if(target==null)return;
        net.minecraft.item.ItemStack output=Pieces.stack(target,1);
        if(output==null)throw new RecoveryAbort("Recovered piece has no item: " + registry + " / " + material,null);
        changes.add(new StackChange(stack,output,Catalogue.INSTANCE.moduleFor(material)));
        // The saved material is retained as unrelated custom data; all other NBT and counts remain untouched.
    }
    private static final class StackChange {
        final NBTTagCompound stack;
        final net.minecraft.item.ItemStack output;
        final String module;
        StackChange(NBTTagCompound stack,net.minecraft.item.ItemStack output,String module){this.stack=stack;this.output=output;this.module=module;}
    }
    public static void prepareChunk(World world,NBTTagCompound root) {
        if(!active())return;
        NBTTagCompound level=root.getCompoundTag("Level");
        NBTTagCompound coverage=level.getCompoundTag("skysbuildingpieces_coverage");
        Set<String> pendingModules=new LinkedHashSet<String>();
        for(String module:Catalogue.INSTANCE.modules.keySet())if(coverage.getInteger(module)<1)pendingModules.add(module);
        if(pendingModules.isEmpty())return;
        NBTTagList tiles=level.getTagList("TileEntities",10),sections=level.getTagList("Sections",10);
        Map<Integer,NBTTagCompound> materialAt=new HashMap<Integer,NBTTagCompound>();
        for(int i=0;i<tiles.tagCount();i++) {NBTTagCompound te=tiles.getCompoundTagAt(i);int index=(te.getInteger("x")&15)|((te.getInteger("z")&15)<<4)|(te.getInteger("y")<<8);materialAt.put(index,te);}
        // Resolve every candidate before modifying any section or deleting a legacy tile.
        List<Conversion> decisions=new ArrayList<Conversion>();
        for(int si=0;si<sections.tagCount();si++) {
            NBTTagCompound section=sections.getCompoundTagAt(si);byte[] blocks=section.getByteArray("Blocks"),add=section.getByteArray("Add"),data=section.getByteArray("Data");
            if(blocks.length!=4096 || data.length!=2048 || add.length!=0 && add.length!=2048)throw new RecoveryAbort("Malformed legacy chunk section",null);
            for(int i=0;i<4096;i++) {
                int numeric=(blocks[i]&255)|(nibble(add,i)<<8);
                Block old=Block.getBlockById(numeric);
                net.minecraft.util.ResourceLocation registeredName=old==null?null:Block.REGISTRY.getNameForObject(old);
                String registry=registeredName==null ? "" : registeredName.toString();
                if(!LegacyMapping.owned(registry)) {
                    // Never reinterpret an already converted Sky or unrelated live ID
                    // through a historical snapshot that used the same numeric slot.
                    if(old==SkysBuildingPieces.HOLDER || old==null || old instanceof net.minecraft.block.BlockAir)registry=savedIds.get(numeric);
                    else continue;
                }
                if(registry==null || !LegacyMapping.owned(registry))continue;
                int key=(i&255)|(((section.getByte("Y")&255)*16+(i>>8))<<8);
                NBTTagCompound te=materialAt.get(key);
                String material=te==null ? "" : te.getString("material");
                String module=Catalogue.INSTANCE.moduleFor(LegacyMapping.material(material));
                if(module!=null && !pendingModules.contains(module))continue;
                Shape shape=LegacyMapping.shape(registry,nibble(data,i));
                IBlockState target=resolve(registry,LegacyMapping.material(material),shape,LegacyMapping.orientation(shape,nibble(data,i)));
                if(target!=null)decisions.add(new Conversion(section,i,target,te,module));
            }
        }
        Set<NBTTagCompound> removed=Collections.newSetFromMap(new IdentityHashMap<NBTTagCompound,Boolean>());
        NBTTagList retainedTileData=coverage.getTagList("retained_tiles",10);
        for(Conversion c:decisions) {
            int id=Block.getIdFromBlock(c.state.getBlock());byte[] low=c.section.getByteArray("Blocks");low[c.index]=(byte)id;
            byte[] add=c.section.getByteArray("Add");if(add.length==0 && id>255)add=new byte[2048];
            if(add.length!=0){setNibble(add,c.index,id>>8);c.section.setByteArray("Add",add);}
            byte[] meta=c.section.getByteArray("Data");setNibble(meta,c.index,c.state.getBlock().getMetaFromState(c.state));
            c.section.setByteArray("Blocks",low);c.section.setByteArray("Data",meta);
            if(c.tile!=null){removed.add(c.tile);retainedTileData.appendTag(c.tile.copy());}
        }
        NBTTagList retained=new NBTTagList();for(int i=0;i<tiles.tagCount();i++)if(!removed.contains(tiles.getCompoundTagAt(i)))retained.appendTag(tiles.getCompoundTagAt(i));level.setTag("TileEntities",retained);
        NBTTagCompound deltas=new NBTTagCompound();
        for(String module:pendingModules){coverage.setInteger(module,1);deltas.setLong(module,0);}
        for(Conversion conversion:decisions)deltas.setLong(conversion.module,deltas.getLong(conversion.module)+1);
        coverage.setTag("retained_tiles",retainedTileData);level.setTag("skysbuildingpieces_coverage",coverage);
        if(pendingModules.contains("vanilla"))root.setLong("skysbuildingpieces_delta",deltas.getLong("vanilla"));
        deltas.removeTag("vanilla");
        if(!deltas.hasNoTags())root.setTag("skysbuildingpieces_module_delta",deltas);
    }
    public static IBlockState resolve(String registry,String material,Shape shape,int orientation) {
        IBlockState target=LegacyMapping.allowedMaterial(material,shape) ? Pieces.state(material,shape,orientation) : null;
        if(target==null) {
            String key=registry+" / "+material+" / "+shape;
            synchronized(unresolved){unresolved.put(key,unresolved.getOrDefault(key,0L)+1);}
            if(!Loader.isModLoaded("buildingbricks"))throw new RecoveryAbort("Cannot safely recover " + key + ". Restore BuildingBricks and its material add-ons, then load a backup. Upgrade was stopped.",null);
        }
        return target;
    }
    public static int nibble(byte[] b,int index) { return b.length==0?0:(b[index>>1]>>((index&1)*4))&15; }
    public static void setNibble(byte[] b,int index,int value) { int shift=(index&1)*4;b[index>>1]=(byte)((b[index>>1]&~(15<<shift))|((value&15)<<shift)); }
    public static synchronized void report(World world) {
        if(!active())return;
        PiecesWorldState state=PiecesWorldState.get(world);
        for(Map.Entry<String,java.util.concurrent.atomic.AtomicLong> entry:pendingItems.entrySet())state.addItems(entry.getKey(),entry.getValue().getAndSet(0));
        synchronized(unresolved) {for(Map.Entry<String,Long> e:unresolved.entrySet())state.unresolved.put(e.getKey(),state.unresolved.getOrDefault(e.getKey(),0L)+e.getValue());unresolved.clear();}state.markDirty();
        File destination=new File(world.getSaveHandler().getWorldDirectory(),"serverconfig/skysbuildingpieces-migration-report.txt");
        destination.getParentFile().mkdirs();
        StringBuilder s=new StringBuilder("Sky's Building Pieces ").append(SkysBuildingPieces.VERSION).append("\nForced replacement: ").append(SkysBuildingPieces.forceReplace).append("\nVanilla blocks: ").append(state.blocks).append("\nVanilla items: ").append(state.items).append("\nVanilla chunks: ").append(state.chunks).append('\n');
        for(Map.Entry<String,PiecesWorldState.Totals> entry:state.modules.entrySet())s.append(entry.getKey()).append(" blocks: ").append(entry.getValue().blocks).append("\n").append(entry.getKey()).append(" items: ").append(entry.getValue().items).append("\n").append(entry.getKey()).append(" chunks: ").append(entry.getValue().chunks).append('\n');
        s.append("Unresolved encounters (including historical reports; left unchanged at encounter):\n");
        for(Map.Entry<String,Long> e:state.unresolved.entrySet())s.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        try{Files.write(destination.toPath(),s.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException e){throw new RecoveryAbort("Cannot write migration report",e);}
    }
    private static final class Conversion {
        final NBTTagCompound section,tile;final int index;final IBlockState state;final String module;
        Conversion(NBTTagCompound section,int index,IBlockState state,NBTTagCompound tile,String module){this.section=section;this.index=index;this.state=state;this.tile=tile;this.module=module;}
    }
    /** An Error deliberately bypasses Forge's catch-and-regenerate chunk fallback. */
    public static final class RecoveryAbort extends Error { public RecoveryAbort(String message,Throwable cause){super(message,cause);} }
}
