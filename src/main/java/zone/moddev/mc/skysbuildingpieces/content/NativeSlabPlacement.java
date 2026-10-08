package zone.moddev.mc.skysbuildingpieces.content;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

/** Exact catalogue slab items only; unknown items and legacy-mod placement remain untouched. */
public final class NativeSlabPlacement {
    private static NativeSlabPlacement instance;
    private final Map<Item,Map<Integer,String>> materials=new HashMap<>();
    private NativeSlabPlacement() {
        for(Catalogue.MaterialDef definition:Catalogue.INSTANCE.materials.values()) {
            if(Pieces.state(definition.id,Shape.VERTICAL_SLAB,0)==null)continue;
            IBlockState slab=Pieces.nativeState(definition,"slab");
            if(slab==null)continue;
            ItemStack item=Pieces.stack(slab,1);
            if(item==null)continue;
            Map<Integer,String> variants=materials.computeIfAbsent(item.getItem(),key->new HashMap<>());
            String previous=variants.put(item.getMetadata(),definition.id);
            if(previous!=null&&!previous.equals(definition.id))throw new IllegalStateException("Conflicting native slab material");
        }
        Item dirt=Item.getByNameOrId("skysgrassslabs:dirt_slab");
        if(dirt!=null){Map<Integer,String> variants=new HashMap<>();variants.put(0,"minecraft:dirt");variants.put(1,"minecraft:dirt");materials.put(dirt,variants);}
    }
    public static void initialize(){instance=new NativeSlabPlacement();}
    public String material(ItemStack stack) {
        Map<Integer,String> variants=stack==null?null:materials.get(stack.getItem());
        return variants==null?null:variants.get(stack.getMetadata());
    }
    /** Null leaves the original item-use path intact; otherwise return its exact result. */
    public static EnumActionResult place(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumHand hand,EnumFacing face,float x,float y,float z) {
        if(instance==null||Loader.isModLoaded("buildingbricks"))return null;
        String material=instance.material(stack);
        // Grass Slabs still owns horizontal dirt placement and its lifecycle.
        if("minecraft:dirt".equals(material)&&SlabPlacement.side(face,x,y,z).getAxis()==EnumFacing.Axis.Y)return null;
        return material==null?null:PieceItem.place(stack,player,world,pos,hand,face,x,y,z,material,Shape.SLAB);
    }
}
