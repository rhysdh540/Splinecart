package io.github.foundationgames.splinecart.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class TrackTiesBlockEntityRenderer implements BlockEntityRenderer<TrackTiesBlockEntity> {
    public static final int WHITE = 0xFFFFFFFF;
    public static final Vector3f WHITEF = new Vector3f(1, 1, 1);
    public static final ResourceLocation TRACK_TEXTURE = Splinecart.id("textures/track.png");
    public static final ResourceLocation TRACK_OVERLAY_TEXTURE = Splinecart.id("textures/track_overlay.png");
    public static final ResourceLocation POSE_TEXTURE_DEBUG = Splinecart.id("textures/debug.png");

    public TrackTiesBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(TrackTiesBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        var start = entity.pose();
        var pos = entity.getBlockPos();

        entity.clientTime += tickDelta;

        if (Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            matrices.pushPose();

            matrices.translate(0.5, 0.5, 0.5);
            var buffer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(POSE_TEXTURE_DEBUG));
            renderDebug(start, matrices.last(), buffer);

            matrices.popPose();
        }

        var buffer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(getTexture()));
        boolean reinitBuffer = false;
        var nextE = entity.next();
        if (nextE != null) {
            var end = nextE.pose();
            var world = entity.getLevel();

            matrices.pushPose();

            matrices.translate(-pos.getX(), -pos.getY(), -pos.getZ());

            var trackType = entity.nextType();

            float u0 = trackType.textureU * 0.25f;
            float u1 = u0 + 0.25f;

            int segs = SplinecartClient.CFG_TRACK_RESOLUTION.get() * Math.max((int) start.translation().distance(end.translation()), 2);
            var origin = new Vector3d(start.translation());
            var basis = new Matrix3d(start.basis());
            var grad = new Vector3d(0, 0, 1).mul(start.basis());
            double[] totalDist = {0};

            for (int i = 0; i < segs; i++) {
                double t0 = (double)i / segs;
                double t1 = (double)(i + 1) / segs;

                renderPart(world, matrices.last(), buffer, start, end, u0, u1, 0, WHITEF, t0, t1, totalDist, origin, basis, grad, overlay);
            }

            if (trackType.overlay != null) {
                reinitBuffer = true;
                var olBuffer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(getTrackOverlayTexture()));

                float[] olVOffset = {0};
                Vector3f olColor = new Vector3f(WHITEF);
                int power = Math.max(entity.power(), nextE.power());
                trackType.overlay.calculateEffects(power, entity.clientTime, olColor, olVOffset);

                for (int i = 0; i < segs; i++) {
                    double t0 = (double)i / segs;
                    double t1 = (double)(i + 1) / segs;

                    renderPart(world, matrices.last(), olBuffer, start, end, u0, u1, olVOffset[0], olColor, t0, t1, totalDist, origin, basis, grad, overlay);
                }
            }

            matrices.popPose();
        }

        var prevE = entity.prev();
        if ((prevE == null) ^ (nextE == null)) {
            if (reinitBuffer) {
                buffer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(getTexture()));
            }

            float z0 = -0.5f;
            float z1 = 0;
            float v0 = 1;
            float v1 = 0.5f;

            if (nextE == null) {
                z0 = 0;
                z1 = 0.5f;
                v0 = 0.5f;
                v1 = 0;
            }

            matrices.pushPose();

            matrices.translate(0.5, 0.5, 0.5);

            var entry = matrices.last();
            var posMat = entry.pose();
            var nmlMat = entry.normal();
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    posMat.setRowColumn(x, y, (float) start.basis().getRowColumn(x, y));
                    nmlMat.setRowColumn(x, y, (float) start.basis().getRowColumn(x, y));
                }
            }

            matrices.translate(0, -0.4375, 0);

            buffer.addVertex(entry, 0.5f, 0, z0).setColor(WHITE).setUv(0.25f, v0).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);
            buffer.addVertex(entry, -0.5f, 0, z0).setColor(WHITE).setUv(0, v0).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);

            buffer.addVertex(entry, -0.5f, 0, z1).setColor(WHITE).setUv(0, v1).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);
            buffer.addVertex(entry, 0.5f, 0, z1).setColor(WHITE).setUv(0.25f, v1).setOverlay(overlay).setLight(light).setNormal(entry, 0, 1, 0);

            matrices.popPose();
        }
    }

    protected ResourceLocation getTexture() {
        return TRACK_TEXTURE;
    }

    protected ResourceLocation getTrackOverlayTexture() {
        return TRACK_OVERLAY_TEXTURE;
    }

    @Override
    public boolean shouldRenderOffScreen(TrackTiesBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return SplinecartClient.CFG_TRACK_RENDER_DISTANCE.get() * 16;
    }

    private void renderDebug(Pose pose, PoseStack.Pose entry, VertexConsumer buffer) {
        var posMat = entry.pose();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                posMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
            }
        }

        buffer.addVertex(entry, 1, 0, 1).setColor(WHITE).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 0, 0, 1).setColor(WHITE).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 0, 0, 0).setColor(WHITE).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 1, 0, 0).setColor(WHITE).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
    }

    private void renderPart(Level world, PoseStack.Pose entry, VertexConsumer buffer, Pose start, Pose end,
                            float u0, float u1, float vOffset, Vector3f color, double t0, double t1, double[] blockProgress,
                            Vector3d origin0, Matrix3d basis0, Vector3d grad0, int overlay) {
        start.interpolate(end, t0, origin0, basis0, grad0);
        var norm0 = new Vector3d(0, 1, 0).mul(basis0);

        var origin1 = new Vector3d(origin0);
        var basis1 = new Matrix3d(basis0);
        var grad1 = new Vector3d(grad0);
        start.interpolate(end, t1, origin1, basis1, grad1);
        var norm1 = new Vector3d(0, 1, 0).mul(basis1);

        float v0 = (float) blockProgress[0];
        while (v0 > 1) v0 -= 1;
        float v1 = v0 + (float) (grad0.length() * (t1 - t0));

        blockProgress[0] = v1;

        v1 = 1 - v1 + vOffset;
        v0 = 1 - v0 + vOffset;

        var pos0 = new BlockPos(Mth.floor(origin0.x()), Mth.floor(origin0.y()), Mth.floor(origin0.z()));
        var pos1 = new BlockPos(Mth.floor(origin1.x()), Mth.floor(origin1.y()), Mth.floor(origin1.z()));

        int light0 = LevelRenderer.getLightColor(world, pos0);
        int light1 = LevelRenderer.getLightColor(world, pos1);

        var point = new Vector3f();

        point.set(0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u0, v0).setOverlay(overlay)
                .setLight(light0).setNormal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());
        point.set(-0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u1, v0).setOverlay(overlay)
                .setLight(light0).setNormal(entry, (float) norm0.x(), (float) norm0.y(), (float) norm0.z());

        point.set(-0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u1, v1).setOverlay(overlay)
                .setLight(light1).setNormal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
        point.set(0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.addVertex(entry, point).setColor(color.x(), color.y(), color.z(), 1).setUv(u0, v1).setOverlay(overlay)
                .setLight(light1).setNormal(entry, (float) norm1.x(), (float) norm1.y(), (float) norm1.z());
    }
}
