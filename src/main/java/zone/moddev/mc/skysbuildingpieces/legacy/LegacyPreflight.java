package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Read-only absent-mod safety check before any asynchronous chunk can deserialize. */
final class LegacyPreflight {
    static void validate(File world,Map<Integer,String> ids) throws IOException {
        final boolean[] candidates=new boolean[4096];for(Integer id:ids.keySet())if(id>=0&&id<4096)candidates[id]=true;
        try(java.util.stream.Stream<Path> paths=Files.walk(world.toPath())) {
            Iterator<Path> files=paths.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).iterator();
            while(files.hasNext()) {
                File file=files.next().toFile();
                try(RandomAccessFile region=new RandomAccessFile(file,"r")) {
                    if(region.length()<8192)throw blocked("malformed region header in "+file.getName());
                    int[] locations=new int[1024];for(int i=0;i<1024;i++)locations[i]=region.readInt();
                    for(int location:locations)if(location!=0) {
                    long offset=(location>>>8)*4096L;region.seek(offset);int length=region.readInt();int compression=region.readUnsignedByte();
                    if(length<1 || length>(location&255)*4096-4 || offset+4+length>region.length())throw blocked("malformed chunk extent in "+file.getName());
                    byte[] payload=new byte[length-1];region.readFully(payload);InputStream bytes=new ByteArrayInputStream(payload);
                    if(compression==1)bytes=new java.util.zip.GZIPInputStream(bytes);
                    else if(compression==2)bytes=new java.util.zip.InflaterInputStream(bytes);
                    else if(compression!=3)throw blocked("unknown region compression in "+file.getName());
                    NBTTagCompound root;try(DataInputStream in=new DataInputStream(bytes)){root=CompressedStreamTools.read(in);}
                    NBTTagCompound level=root.getCompoundTag("Level");
                    {
                        Map<Integer,NBTTagCompound> tiles=new HashMap<Integer,NBTTagCompound>();NBTTagList tileList=level.getTagList("TileEntities",10);
                        for(int i=0;i<tileList.tagCount();i++){NBTTagCompound n=tileList.getCompoundTagAt(i);tiles.put((n.getInteger("x")&15)|((n.getInteger("z")&15)<<4)|(n.getInteger("y")<<8),n);}
                        NBTTagList sections=level.getTagList("Sections",10);
                        for(int s=0;s<sections.tagCount();s++) {
                            NBTTagCompound section=sections.getCompoundTagAt(s);byte[] low=section.getByteArray("Blocks"),add=section.getByteArray("Add"),meta=section.getByteArray("Data");
                            if(low.length!=4096||meta.length!=2048||add.length!=0&&add.length!=2048)throw blocked("malformed section in "+file.getName());
                            for(int i=0;i<4096;i++) {
                                int id=(low[i]&255)|(LegacyBridge.nibble(add,i)<<8);if(!candidates[id])continue;
                                String name=ids.get(id);NBTTagCompound tile=tiles.get((i&255)|(((section.getByte("Y")&255)*16+(i>>8))<<8));
                                String material=LegacyMapping.material(tile==null?"":tile.getString("material"));Shape shape=LegacyMapping.shape(name,LegacyBridge.nibble(meta,i));
                                if(!LegacyMapping.allowedMaterial(material,shape)||Pieces.state(material,shape,LegacyMapping.orientation(shape,LegacyBridge.nibble(meta,i)))==null)
                                    throw blocked(name+" / "+material+" / "+shape+" in "+file.getName()+" chunk "+level.getInteger("xPos")+", "+level.getInteger("zPos"));
                            }
                        }
                    }
                    stacks(root);
                }}
            }
        }
        File players=new File(world,"playerdata");File[] files=players.listFiles((dir,name)->name.endsWith(".dat"));
        if(files!=null)for(File file:files)try(InputStream in=new FileInputStream(file)){stacks(CompressedStreamTools.readCompressed(in));}
    }
    private static void stacks(NBTBase value) {
        if(value instanceof NBTTagCompound) {
            NBTTagCompound n=(NBTTagCompound)value;String id=n.getString("id");
            if(n.hasKey("Count",99)&&LegacyMapping.owned(id)) {
                String material=LegacyMapping.material(n.getCompoundTag("tag").getString("material"));Shape shape=LegacyMapping.shape(id,0);
                if(!LegacyMapping.allowedMaterial(material,shape)||Pieces.state(material,shape,shape==Shape.SLAB?1:0)==null)throw blocked(id+" / "+material+" / "+shape+" item");
            }
            for(String key:n.getKeySet())if(!key.equals("retained_tiles"))stacks(n.getTag(key));
        } else if(value instanceof NBTTagList){NBTTagList list=(NBTTagList)value;for(int i=0;i<list.tagCount();i++)stacks(list.get(i));}
    }
    private static LegacyBridge.RecoveryAbort blocked(String detail) {
        return new LegacyBridge.RecoveryAbort("World upgrade stopped before chunk loading: "+detail+" has no safe target. Restore BuildingBricks and its material add-ons, then use a backup. No unsupported material was substituted.",null);
    }
}
