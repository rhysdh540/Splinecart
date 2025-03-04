package io.github.foundationgames.splinecart.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {LevelRenderer.class}, priority = 1500)
public class WorldRendererMixin {
    @ModifyExpressionValue(method = "setupRender",
            require = 0, at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
    private boolean splinecart$updateChunkOcclusionCullingWhileOnTrack(boolean old) {
        if (SplinecartClient.CFG_ROTATE_CAMERA.get()) {
            var entity = Minecraft.getInstance().cameraEntity;
            while (entity != null) {
                entity = entity.getVehicle();

                if (entity instanceof TrackFollowerEntity) {
                    return true;
                }
            }
        }

        return old;
    }
}
