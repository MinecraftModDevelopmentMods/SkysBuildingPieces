package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public final class PiecesWorldState extends WorldSavedData {
    public static final String NAME="skysbuildingpieces_world_state";
    public long blocks,items,chunks;
    public final java.util.Map<String,Long> unresolved=new java.util.TreeMap<String,Long>();
    public final java.util.Map<String,Totals> modules=new java.util.TreeMap<String,Totals>();
    public static final class Totals { public long blocks,items,chunks; }
    private Totals totals(String module) {return modules.computeIfAbsent(module,k->new Totals());}
    public void addBlocks(String module,long count) {if(module.equals("vanilla"))blocks+=count;else totals(module).blocks+=count;}
    public void addItems(String module,long count) {if(count==0)return;if(module.equals("vanilla"))items+=count;else totals(module).items+=count;}
    public void addChunk(String module,long count) {addBlocks(module,count);if(module.equals("vanilla"))chunks++;else totals(module).chunks++;}
    private NBTTagCompound retained=new NBTTagCompound();
    public PiecesWorldState() { super(NAME); }
    public PiecesWorldState(String name) { super(name); }
    public void readFromNBT(NBTTagCompound n) {
        int schema=n.getInteger("schema_version");if(schema!=1)throw new IllegalStateException("Unsupported building-pieces state schema " + schema);
        blocks=n.getLong("vanilla_blocks");items=n.getLong("vanilla_items");chunks=n.getLong("vanilla_chunks");
        retained=n.copy();unresolved.clear();NBTTagCompound issues=n.getCompoundTag("unresolved");
        for(String key:issues.getKeySet())unresolved.put(key,issues.getLong(key));
        modules.clear();NBTTagCompound savedModules=n.getCompoundTag("modules");
        for(String key:savedModules.getKeySet()){NBTTagCompound saved=savedModules.getCompoundTag(key);Totals t=totals(key);t.blocks=saved.getLong("blocks");t.items=saved.getLong("items");t.chunks=saved.getLong("chunks");}
    }
    public NBTTagCompound writeToNBT(NBTTagCompound n) {
        n.merge(retained);n.setInteger("schema_version",1);n.setLong("vanilla_blocks",blocks);n.setLong("vanilla_items",items);n.setLong("vanilla_chunks",chunks);
        NBTTagCompound issues=new NBTTagCompound();for(java.util.Map.Entry<String,Long> e:unresolved.entrySet())issues.setLong(e.getKey(),e.getValue());n.setTag("unresolved",issues);
        NBTTagCompound savedModules=n.getCompoundTag("modules");
        for(java.util.Map.Entry<String,Totals> e:modules.entrySet()){NBTTagCompound saved=savedModules.getCompoundTag(e.getKey());saved.setLong("blocks",e.getValue().blocks);saved.setLong("items",e.getValue().items);saved.setLong("chunks",e.getValue().chunks);savedModules.setTag(e.getKey(),saved);}
        if(!savedModules.hasNoTags())n.setTag("modules",savedModules);return n;
    }
    public static PiecesWorldState get(World world) {
        if(world.getMinecraftServer()!=null)world=world.getMinecraftServer().worldServerForDimension(0);
        PiecesWorldState s=(PiecesWorldState)world.getMapStorage().getOrLoadData(PiecesWorldState.class,NAME);
        if(s==null) {s=new PiecesWorldState();world.getMapStorage().setData(NAME,s);s.markDirty();}return s;
    }
}
