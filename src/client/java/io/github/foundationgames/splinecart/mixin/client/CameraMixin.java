package io.github.foundationgames.splinecart.mixin.client;

import com.mojang.math.Axis;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow @Final private Quaternionf rotation;

    @Inject(method = "setup",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0, target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void splinecart$updateCamPosWhileRiding(BlockGetter area, Entity self, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info) {
        var vehicle = self.getVehicle();
        if (vehicle != null) {
            var tf = vehicle.getVehicle();
            if (tf instanceof TrackFollowerEntity trackFollower) {
                var world = self.level();
                var diff = self.position().add(0, self.getEyeHeight(), 0).subtract(trackFollower.position());
                var camPos = new Vector3d(diff.x(), diff.y(), diff.z());
                if (world.isClientSide()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, tickDelta);
                    rot.transform(camPos);

                    this.setPosition(new Vec3(camPos.x(), camPos.y(), camPos.z()).add(trackFollower.getPosition(tickDelta)));

                    if (SplinecartClient.CFG_ROTATE_CAMERA.get()) {
                        rot.mul(Axis.YP.rotationDegrees(90 + vehicle.getViewYRot(tickDelta)).mul(rotation, rotation), rotation);
                    }
                }
            }
        }
    }
}
