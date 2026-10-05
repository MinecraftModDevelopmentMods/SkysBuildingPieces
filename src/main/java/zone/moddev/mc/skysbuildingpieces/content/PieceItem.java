package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

/** Direct placement, deliberately not delegated to BuildingBricks' ItemBlock interception. */
public final class PieceItem extends ItemBlock {
    public final PieceBlock piece;
    public PieceItem(PieceBlock block) { super(block);piece=block;setHasSubtypes(true); }
    public int getMetadata(int damage) { return damage; }
    public String getUnlocalizedName(ItemStack s) { return "tile."+piece.palette.namespace+"."+new ResourceLocation(piece.palette.material(s.getMetadata())).getResourcePath()+"."+piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT); }
    public String getItemStackDisplayName(ItemStack stack) {
        Catalogue.MaterialDef def=Catalogue.INSTANCE.materials.get(piece.palette.material(stack.getMetadata()));
        ItemStack full=Pieces.stack(Pieces.nativeState(def,"full"),1);
        String material=full==null ? net.minecraft.util.text.translation.I18n.translateToLocal("tile.stoneSlab.stone.name") : full.getDisplayName();
        String shape=piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT),key="pieces."+piece.palette.namespace+"."+shape;
        if(!net.minecraft.util.text.translation.I18n.canTranslate(key))key="pieces.skysbuildingpieces."+shape;
        return net.minecraft.util.text.translation.I18n.translateToLocalFormatted(key,material);
    }
    public EnumActionResult onItemUse(ItemStack stack,EntityPlayer player,World w,BlockPos p,EnumHand hand,EnumFacing face,float x,float y,float z) {
        if(stack==null || stack.stackSize==0)return EnumActionResult.FAIL;
        String mat=piece.palette.material(stack.getMetadata());
        float targetX=x,targetY=y,targetZ=z;
        IBlockState old=w.getBlockState(p);
        if(!old.getBlock().isReplaceable(w,p)) {
            if(combine(w,p,old,mat,player,stack,face,x,y,z))return EnumActionResult.SUCCESS;
            p=p.offset(face);old=w.getBlockState(p);
            // Subsequent combination checks use coordinates in the destination,
            // not the block that was clicked (its boundary is the opposite one).
            targetX-=face.getFrontOffsetX();targetY-=face.getFrontOffsetY();targetZ-=face.getFrontOffsetZ();
        }
        if(!player.canPlayerEdit(p,face,stack))return EnumActionResult.FAIL;
        if(!old.getBlock().isReplaceable(w,p))return combine(w,p,old,mat,player,stack,face,targetX,targetY,targetZ)?EnumActionResult.SUCCESS:EnumActionResult.FAIL;
        int rot=closest(x,z);int orientation;Shape placedShape=piece.palette.shape;
        switch(piece.palette.shape) {
            case SLAB:
                EnumFacing slabSide=SlabPlacement.side(face,x,y,z);
                placedShape=SlabPlacement.shape(slabSide);orientation=SlabPlacement.orientation(slabSide);break;
            case VERTICAL_SLAB: orientation=SlabPlacement.orientation(SlabPlacement.verticalSide(face,x,y,z));break;
            case HORIZONTAL_STEP: orientation=rot+(face==EnumFacing.DOWN||face.getAxis()!=EnumFacing.Axis.Y && y>.5f?0:4);break;
            case VERTICAL_STEP: orientation=corner(x,z);break;
            case STEP: orientation=player.isSneaking()?8+corner(x,z):rot+(face==EnumFacing.DOWN||face.getAxis()!=EnumFacing.Axis.Y&&y>.5f?0:4);break;
            case CORNER: orientation=corner(x,z)+(face==EnumFacing.DOWN||face.getAxis()!=EnumFacing.Axis.Y&&y>.5f?0:4);break;
            case STAIRS: EnumFacing f=player.getHorizontalFacing();orientation=(f==EnumFacing.EAST?0:f==EnumFacing.WEST?1:f==EnumFacing.SOUTH?2:3)+(face==EnumFacing.DOWN||face.getAxis()!=EnumFacing.Axis.Y&&y>.5f?4:0);break;
            default: orientation=0;
        }
        IBlockState target=Pieces.state(mat,placedShape,orientation);
        if(piece.palette.shape==Shape.HORIZONTAL_STEP && player.isSneaking())target=Pieces.state(mat,Shape.VERTICAL_STEP,corner(x,z));
        // World placement checks the block's default bounding box. Collision
        // must instead use this exact palette orientation and derived shape.
        if(target==null || !w.canBlockBePlaced(target.getBlock(),p,true,face,player,stack)||!safe(w,p,target))return EnumActionResult.FAIL;
        if(!w.setBlockState(p,target,3))return EnumActionResult.FAIL;
        target.getBlock().onBlockPlacedBy(w,p,target,player,stack);finish(w,p,target,player,stack);return EnumActionResult.SUCCESS;
    }
    public boolean canPlaceBlockOnSide(World w,BlockPos p,EnumFacing side,EntityPlayer player,ItemStack s) { return player.canPlayerEdit(p,side,s); }
    private boolean combine(World w,BlockPos p,IBlockState old,String material,EntityPlayer player,ItemStack stack,EnumFacing face,float x,float y,float z) {
        if(!(old.getBlock() instanceof PieceBlock) || !player.canPlayerEdit(p,face,stack))return false;
        PieceBlock other=(PieceBlock)old.getBlock();
        if(!other.definition(old).id.equals(material) || other.palette.shape==Shape.WALL||other.palette.shape==Shape.PANE || piece.palette.shape==Shape.WALL||piece.palette.shape==Shape.PANE)return false;
        // On an internal half-block face, select the empty side of the face,
        // including DOWN/WEST/NORTH clicks exactly on the 0.5 boundary.
        float epsilon=.0001f;
        int oldMask=other.mask(old),pick=(x+face.getFrontOffsetX()*epsilon>=.5f?1:0)|(z+face.getFrontOffsetZ()*epsilon>=.5f?2:0)|(y+face.getFrontOffsetY()*epsilon>=.5f?4:0);
        IBlockState result=null;
        boolean slab=piece.palette.shape==Shape.SLAB||piece.palette.shape==Shape.VERTICAL_SLAB;
        Shape[] additions=slab?new Shape[]{Shape.SLAB,Shape.VERTICAL_SLAB}:new Shape[]{piece.palette.shape};
        search: for(Shape addition:additions)for(int orientation=0;orientation<addition.states;orientation++) {
            int add=Geometry.mask(addition,orientation);
            if((add & 1<<pick)==0 || (add&oldMask)!=0)continue;
            int combined=add|oldMask;
            if(combined==255)result=Pieces.nativeState(Catalogue.INSTANCE.materials.get(material),"full");
            else for(Shape s:new Shape[]{Shape.SLAB,Shape.VERTICAL_SLAB,Shape.HORIZONTAL_STEP,Shape.VERTICAL_STEP,Shape.STEP,Shape.CORNER,Shape.STAIRS}) {
                int o=Geometry.orientationFor(s,combined);if(o>=0) {result=Pieces.state(material,s,o);if(result!=null)break;}
            }
            if(result!=null)break search;
        }
        if(result==null || !safe(w,p,result) || !w.setBlockState(p,result,3))return false;
        finish(w,p,result,player,stack);return true;
    }
    private static boolean safe(World w,BlockPos p,IBlockState target) {
        if(target.getBlock() instanceof PieceBlock)for(net.minecraft.util.math.AxisAlignedBB b:((PieceBlock)target.getBlock()).boxes(target.getBlock().getActualState(target,w,p)))if(!w.checkNoEntityCollision(b.offset(p)))return false;
        return !(target.getBlock() instanceof PieceBlock) ? w.checkNoEntityCollision(new net.minecraft.util.math.AxisAlignedBB(p)) : true;
    }
    private static void finish(World w,BlockPos p,IBlockState target,EntityPlayer player,ItemStack stack) {
        net.minecraft.block.SoundType sound=target.getBlock().getSoundType(target,w,p,player);
        w.playSound(player,p,sound.getPlaceSound(),SoundCategory.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8f);
        if(!player.capabilities.isCreativeMode)stack.stackSize--;
    }
    private static int closest(float x,float z) { float[] dist={z,1-x,1-z,x};int best=0;for(int i=1;i<4;i++)if(dist[i]<dist[best])best=i;return best; }
    private static int corner(float x,float z) { return z<.5f ? (x<.5f?0:1) : (x<.5f?3:2); }
}
