package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Draws the wall silhouette instead of a box spanning its empty shoulders. */
public final class WallHighlight {
    @SubscribeEvent
    public void draw(DrawBlockHighlightEvent event) {
        RayTraceResult target = event.getTarget();

        if (event.getSubID() != 0 || target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }

        EntityPlayer player = event.getPlayer();
        BlockPos pos = target.getBlockPos();

        if (!player.getEntityWorld().getWorldBorder().contains(pos)) {
            return;
        }

        IBlockState state = player.getEntityWorld().getBlockState(pos);

        if (!(state.getBlock() instanceof PieceBlock)) {
            return;
        }

        PieceBlock block = (PieceBlock) state.getBlock();

        if (block.palette.shape != Shape.WALL) {
            return;
        }

        state = block.getActualState(state, player.getEntityWorld(), pos);

        double cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);

        try {
            VertexBuffer buffer = Tessellator.getInstance().getBuffer();

            buffer.begin(org.lwjgl.opengl.GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

            for (WallGeometry.Edge edge : WallGeometry.outline(state.getValue(PieceBlock.CONNECTIONS),
                    state.getValue(BlockWall.UP))) {
                vertex(buffer, pos.getX() + edge.x0 / 16.0 - cameraX, pos.getY() + edge.y0 / 16.0 - cameraY,
                        pos.getZ() + edge.z0 / 16.0 - cameraZ, player.getEyeHeight());
                vertex(buffer, pos.getX() + edge.x1 / 16.0 - cameraX, pos.getY() + edge.y1 / 16.0 - cameraY,
                        pos.getZ() + edge.z1 / 16.0 - cameraZ, player.getEyeHeight());
            }

            Tessellator.getInstance().draw();
            event.setCanceled(true);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.glLineWidth(1);
        }
    }

    private static void vertex(VertexBuffer buffer, double vertexX, double vertexY, double vertexZ, double eyeHeight) {
        // A small camera-facing offset keeps the line clear of the wall surface.
        double relativeY = vertexY - eyeHeight;
        double distance = Math.sqrt(vertexX * vertexX + relativeY * relativeY + vertexZ * vertexZ);

        double scale = distance > .002 ? 1 - .002 / distance : 1;

        buffer.pos(vertexX * scale, eyeHeight + relativeY * scale, vertexZ * scale).color(0, 0, 0, .4f).endVertex();
    }
}
