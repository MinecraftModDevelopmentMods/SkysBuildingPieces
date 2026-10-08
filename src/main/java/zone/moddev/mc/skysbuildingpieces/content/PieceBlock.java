package zone.moddev.mc.skysbuildingpieces.content;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.IPlantable;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

/** Metadata palettes with derived visuals; no tile entities or saved composites. */
public final class PieceBlock extends Block implements IGrowable {
    public static final PropertyInteger META=PropertyInteger.create("meta",0,15);
    public static final PropertyInteger CONNECTIONS=PropertyInteger.create("connections",0,15);
    public static final PropertyEnum<BlockStairs.EnumShape> STAIR_SHAPE=BlockStairs.SHAPE;
    private static final ThreadLocal<Catalogue.Palette> CONSTRUCTING=new ThreadLocal<Catalogue.Palette>();
    public final Catalogue.Palette palette;
    public PieceBlock(Catalogue.Palette palette) {
        super(materialFor(palette));this.palette=palette;CONSTRUCTING.remove();
        setRegistryName(palette.namespace,palette.id);setUnlocalizedName(palette.namespace+"."+palette.id);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.BUILDING_BLOCKS);useNeighborBrightness=true;
        setLightOpacity(0);setTickRandomly(palette.group.equals("grass") || palette.group.equals("dirt"));
        slipperiness=(palette.group.equals("ice") || palette.group.equals("packed_ice")) ? 0.98f : 0.6f;
        IBlockState initial=blockState.getBaseState().withProperty(META,0);
        if(initial.getPropertyKeys().contains(BlockGrass.SNOWY))initial=initial.withProperty(BlockGrass.SNOWY,false);
        if(initial.getPropertyKeys().contains(CONNECTIONS))initial=initial.withProperty(CONNECTIONS,0);
        if(initial.getPropertyKeys().contains(STAIR_SHAPE))initial=initial.withProperty(STAIR_SHAPE,BlockStairs.EnumShape.STRAIGHT);
        setDefaultState(initial);
    }
    private static Material materialFor(Catalogue.Palette p) {
        CONSTRUCTING.set(p);String g=p.group;
        if(!p.module.equals("vanilla"))return Pieces.nativeState(Catalogue.INSTANCE.materials.get(p.materials.get(0)),"full").getMaterial();
        if(g.equals("metal"))return Material.IRON;if(g.equals("wood"))return Material.WOOD;
        if(g.equals("dirt"))return Material.GROUND;if(g.equals("grass"))return Material.GRASS;
        if(g.equals("cloth"))return Material.CLOTH;if(g.contains("glass"))return Material.GLASS;
        if(g.equals("ice"))return Material.ICE;if(g.equals("packed_ice"))return Material.PACKED_ICE;
        if(g.equals("snow"))return Material.CRAFTED_SNOW;return Material.ROCK;
    }
    protected BlockStateContainer createBlockState() {
        Catalogue.Palette p=CONSTRUCTING.get();if(p==null)throw new IllegalStateException("Palette construction context missing");
        List<IProperty<?>> properties=new ArrayList<IProperty<?>>();properties.add(META);
        if(p.group.equals("grass")||p.group.equals("dirt"))properties.add(BlockGrass.SNOWY);
        if(p.shape==Shape.WALL||p.shape==Shape.PANE)properties.add(CONNECTIONS);
        if(p.shape==Shape.STAIRS)properties.add(STAIR_SHAPE);
        return new BlockStateContainer(this,properties.toArray(new IProperty<?>[properties.size()]));
    }
    public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(META,meta&15); }
    public int getMetaFromState(IBlockState s) { return s.getValue(META); }
    public Catalogue.MaterialDef definition(IBlockState s) { return Catalogue.INSTANCE.materials.get(palette.material(getMetaFromState(s))); }
    public int orientation(IBlockState s) { return getMetaFromState(s)%palette.shape.states; }
    public int canonicalMeta(IBlockState s) { return getMetaFromState(s)/palette.shape.states*palette.shape.states; }
    public int mask(IBlockState s) { return palette.shape==Shape.STAIRS ? Geometry.stairs(orientation(s),s.getValue(STAIR_SHAPE).getName()) : Geometry.mask(palette.shape,orientation(s)); }
    public boolean isOpaqueCube(IBlockState s) { return false; }
    public boolean isFullCube(IBlockState s) { return false; }
    public boolean isSideSolid(IBlockState s,IBlockAccess w,BlockPos p,EnumFacing side) {
        return palette.shape!=Shape.WALL && palette.shape!=Shape.PANE && Geometry.completeFace(mask(s),side.getIndex());
    }
    public boolean doesSideBlockRendering(IBlockState s,IBlockAccess w,BlockPos p,EnumFacing side) {
        return getMaterial(s).isOpaque() && isSideSolid(s,w,p,side);
    }
    // Internal surfaces are never culled by a neighbour outside their plane.
    public boolean shouldSideBeRendered(IBlockState s,IBlockAccess w,BlockPos p,EnumFacing side) { return true; }
    public BlockRenderLayer getBlockLayer() {
        if(palette.group.equals("grass"))return BlockRenderLayer.CUTOUT_MIPPED;
        if(palette.group.equals("clear_glass"))return BlockRenderLayer.CUTOUT;
        if(palette.group.equals("stained_glass") || palette.group.equals("ice"))return BlockRenderLayer.TRANSLUCENT;
        return BlockRenderLayer.SOLID;
    }
    public float getBlockHardness(IBlockState s,World w,BlockPos p) { Catalogue.MaterialDef m=definition(s);IBlockState full=m.nativeProperties()?Pieces.nativeState(m,"full"):null;return full==null?m.hardness():full.getBlock().getBlockHardness(full,w,p); }
    public float getExplosionResistance(World w,BlockPos p,Entity e,Explosion explosion) { Catalogue.MaterialDef m=definition(w.getBlockState(p));if(m.explosionResistance()>=0)return m.explosionResistance();if(m.nativeProperties())return Pieces.nativeState(m,"full").getBlock().getExplosionResistance(e);return m.resistance()<0?m.hardness():m.resistance()*3/5; }
    public String getHarvestTool(IBlockState s) {
        String g=definition(s).type;return g.equals("rock")||g.equals("metal") ? "pickaxe" : g.equals("wood") ? "axe" : g.equals("dirt")||g.equals("grass")||g.equals("snow") ? "shovel" : null;
    }
    public int getHarvestLevel(IBlockState s) { return Pieces.nativeState(definition(s),"full").getBlock().getHarvestLevel(Pieces.nativeState(definition(s),"full")); }
    public SoundType getSoundType(IBlockState s,World w,BlockPos p,Entity e) {
        IBlockState full=Pieces.nativeState(definition(s),"full");return full.getBlock().getSoundType(full,w,p,e);
    }
    public int getFlammability(IBlockAccess w,BlockPos p,EnumFacing face) { Catalogue.MaterialDef m=definition(w.getBlockState(p));if(m.flammability()>=0)return m.flammability();IBlockState full=Pieces.nativeState(m,"full");return full.getBlock().getFlammability(w,p,face); }
    public int getFireSpreadSpeed(IBlockAccess w,BlockPos p,EnumFacing face) { Catalogue.MaterialDef m=definition(w.getBlockState(p));if(m.fireSpread()>=0)return m.fireSpread();IBlockState full=Pieces.nativeState(m,"full");return full.getBlock().getFireSpreadSpeed(w,p,face); }
    public boolean canSustainPlant(IBlockState s,IBlockAccess w,BlockPos p,EnumFacing facing,IPlantable plant) {
        if(facing!=EnumFacing.UP || !Geometry.completeTop(mask(s)) || !definition(s).soil())return false;
        IBlockState full=Pieces.nativeState(definition(s),"full");return full.getBlock().canSustainPlant(full,w,p,facing,plant);
    }
    public List<AxisAlignedBB> boxes(IBlockState s) {
        List<AxisAlignedBB> boxes=new ArrayList<AxisAlignedBB>();
        if(palette.shape==Shape.WALL || palette.shape==Shape.PANE) {
            double a=palette.shape==Shape.WALL ? .25 : .4375,b=1-a;
            boxes.add(new AxisAlignedBB(a,0,a,b,1,b));
            int c=s.getValue(CONNECTIONS);
            if((c&1)!=0)boxes.add(new AxisAlignedBB(a,0,0,b,1,a));
            if((c&2)!=0)boxes.add(new AxisAlignedBB(b,0,a,1,1,b));
            if((c&4)!=0)boxes.add(new AxisAlignedBB(a,0,b,b,1,1));
            if((c&8)!=0)boxes.add(new AxisAlignedBB(0,0,a,a,1,b));
        } else {
            int m=mask(s);
            for(int i=0;i<8;i++)if((m&(1<<i))!=0) { double x=(i&1)*.5,z=((i>>1)&1)*.5,y=((i>>2)&1)*.5;boxes.add(new AxisAlignedBB(x,y,z,x+.5,y+.5,z+.5)); }
        }
        return boxes;
    }
    public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p) {
        List<AxisAlignedBB> b=boxes(getActualState(s,w,p));AxisAlignedBB result=b.get(0);for(int i=1;i<b.size();i++)result=result.union(b.get(i));return result;
    }
    public void addCollisionBoxToList(IBlockState s,World w,BlockPos p,AxisAlignedBB entityBox,List<AxisAlignedBB> list,Entity entity) {
        for(AxisAlignedBB b:boxes(getActualState(s,w,p))) {
            if(palette.shape==Shape.WALL)b=new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,1.5,b.maxZ);
            addCollisionBoxToList(p,entityBox,list,b);
        }
    }
    public RayTraceResult collisionRayTrace(IBlockState s,World w,BlockPos p,Vec3d from,Vec3d to) {
        RayTraceResult closest=null;double distance=Double.MAX_VALUE;
        for(AxisAlignedBB b:boxes(getActualState(s,w,p))) { RayTraceResult hit=rayTrace(p,from,to,b);
            if(hit!=null && hit.hitVec.squareDistanceTo(from)<distance) {closest=hit;distance=hit.hitVec.squareDistanceTo(from);} }
        return closest;
    }
    public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p) {
        boolean snow=false;
        for(EnumFacing face:new EnumFacing[]{EnumFacing.UP,EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST}) {
            Block b=w.getBlockState(p.offset(face)).getBlock();snow|=b==Blocks.SNOW || b==Blocks.SNOW_LAYER;
        }
        int c=0;int bit=1;
        for(EnumFacing face:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST}) {
            IBlockState n=w.getBlockState(p.offset(face));
            boolean compatible=n.getBlock() instanceof PieceBlock && ((PieceBlock)n.getBlock()).palette.shape==palette.shape;
            if(compatible || n.getBlock() instanceof BlockWall || n.getBlock() instanceof BlockPane || n.getBlock() instanceof BlockFenceGate || n.isSideSolid(w,p.offset(face),face.getOpposite()))c|=bit;
            bit<<=1;
        }
        if(palette.shape==Shape.STAIRS)s=s.withProperty(STAIR_SHAPE,stairsShape(s,w,p));
        if(s.getPropertyKeys().contains(BlockGrass.SNOWY))s=s.withProperty(BlockGrass.SNOWY,snow);
        if(s.getPropertyKeys().contains(CONNECTIONS))s=s.withProperty(CONNECTIONS,c);
        return s;
    }
    private static EnumFacing stairsFacing(int meta) { return new EnumFacing[]{EnumFacing.EAST,EnumFacing.WEST,EnumFacing.SOUTH,EnumFacing.NORTH}[meta&3]; }
    private Integer stairMeta(IBlockState s,String material) {
        if(s.getBlock() instanceof PieceBlock) {PieceBlock b=(PieceBlock)s.getBlock();return b.palette.shape==Shape.STAIRS && b.definition(s).id.equals(material)?b.orientation(s):null;}
        if(s.getBlock() instanceof BlockStairs) {
            IBlockState nativeStairs=Pieces.nativeState(Catalogue.INSTANCE.materials.get(material),"stairs");
            if(nativeStairs!=null && nativeStairs.getBlock()==s.getBlock())return s.getBlock().getMetaFromState(s)&7;
        }
        return null;
    }
    private boolean different(IBlockState s,IBlockAccess w,BlockPos p,EnumFacing offset) {
        Integer n=stairMeta(w.getBlockState(p.offset(offset)),definition(s).id);return n==null||n!=orientation(s);
    }
    private BlockStairs.EnumShape stairsShape(IBlockState s,IBlockAccess w,BlockPos p) {
        EnumFacing f=stairsFacing(orientation(s));Integer front=stairMeta(w.getBlockState(p.offset(f)),definition(s).id);
        if(front!=null && (front&4)==(orientation(s)&4)) {EnumFacing n=stairsFacing(front);if(n.getAxis()!=f.getAxis()&&different(s,w,p,n.getOpposite()))return n==f.rotateYCCW()?BlockStairs.EnumShape.OUTER_LEFT:BlockStairs.EnumShape.OUTER_RIGHT;}
        Integer back=stairMeta(w.getBlockState(p.offset(f.getOpposite())),definition(s).id);
        if(back!=null && (back&4)==(orientation(s)&4)) {EnumFacing n=stairsFacing(back);if(n.getAxis()!=f.getAxis()&&different(s,w,p,n))return n==f.rotateYCCW()?BlockStairs.EnumShape.INNER_LEFT:BlockStairs.EnumShape.INNER_RIGHT;}
        return BlockStairs.EnumShape.STRAIGHT;
    }
    public List<ItemStack> getDrops(IBlockAccess w,BlockPos p,IBlockState s,int fortune) {
        String drop=definition(s).normalDrop();if(drop.isEmpty())return Collections.emptyList();
        IBlockState dropped=Pieces.state(drop,palette.shape,orientation(s));
        ItemStack stack=palette.shape==Shape.VERTICAL_SLAB?Pieces.slabDrop(drop,dropped):Pieces.stack(dropped,1);
        return stack==null ? Collections.<ItemStack>emptyList() : Collections.singletonList(stack);
    }
    protected ItemStack getSilkTouchDrop(IBlockState s) { return palette.shape==Shape.VERTICAL_SLAB?Pieces.slabDrop(definition(s).id,s):Pieces.stack(s,1); }
    public boolean canSilkHarvest(World w,BlockPos p,IBlockState s,EntityPlayer player) { return true; }
    public int damageDropped(IBlockState s) { return canonicalMeta(s); }
    public ItemStack getPickBlock(IBlockState s,RayTraceResult hit,World w,BlockPos p,EntityPlayer player) { return palette.shape==Shape.VERTICAL_SLAB?Pieces.slabDrop(definition(s).id,s):Pieces.stack(s,1); }
    public void getSubBlocks(Item item,net.minecraft.creativetab.CreativeTabs tab,List<ItemStack> list) {
        if(palette.shape==Shape.VERTICAL_SLAB)return;
        for(int slot=0;slot<palette.materials.size();slot++) list.add(new ItemStack(item,1,slot*palette.shape.states));
    }
    public void updateTick(World w,BlockPos p,IBlockState s,Random rand) {
        GrassLifecycle.tick(w,p,s,rand);
    }
    public void onBlockAdded(World w,BlockPos p,IBlockState s) { GrassLifecycle.repairSupport(w,p,s); }
    public void neighborChanged(IBlockState s,World w,BlockPos p,Block changed) { GrassLifecycle.repairSupport(w,p,s); }
    public boolean canGrow(World w,BlockPos p,IBlockState s,boolean client) { return GrassLifecycle.grass(s) && Geometry.completeTop(mask(s)); }
    public boolean canUseBonemeal(World w,Random rand,BlockPos p,IBlockState s) { return canGrow(w,p,s,w.isRemote); }
    public void grow(World w,Random rand,BlockPos p,IBlockState s) {
        if(canGrow(w,p,s,w.isRemote))Blocks.GRASS.grow(w,rand,p,Blocks.GRASS.getDefaultState());
    }
}
