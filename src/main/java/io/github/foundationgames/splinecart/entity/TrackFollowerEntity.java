package io.github.foundationgames.splinecart.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class TrackFollowerEntity extends Entity {
    public static final double FRICTION = 0.997;
    public static final double CHAIN_DRIVE_SPEED = 0.36;
    public static final double MAGNETIC_SPEED_FACTOR = 1.6;
    public static final double MAGNETIC_ACCEL = 0.07;

    private static final double GRAVITY = 0.04;

    private @Nullable BlockPos startTie;
    private @Nullable BlockPos endTie;
    private double splinePieceProgress = 0; // t
    private double motionScale; // t-distance per block
    private double trackVelocity;

    private final Vector3d serverPosition = new Vector3d();
    private final Vector3d serverVelocity = new Vector3d();
    private int positionInterpSteps;
    private int oriInterpSteps;

    private static final EntityDataAccessor<Quaternionf> ORIENTATION = SynchedEntityData.defineId(TrackFollowerEntity.class, EntityDataSerializers.QUATERNION);
    private final Matrix3d basis = new Matrix3d().identity();

    private final Quaternionf lastClientOrientation = new Quaternionf();
    private final Quaternionf clientOrientation = new Quaternionf();

    private boolean hadPassenger = false;

    private boolean firstPositionUpdate = true;
    private boolean firstOriUpdate = true;

    private Vec3 clientMotion = Vec3.ZERO;

    public TrackFollowerEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public TrackFollowerEntity(Level world) {
        this(Splinecart.TRACK_FOLLOWER.get(), world);
    }

    public static @Nullable TrackFollowerEntity create(Level world, Vec3 startPos, BlockPos tie, Vec3 velocity) {
        var tieE = TrackTiesBlockEntity.of(world, tie);
        double trackVelocity, progress;
        BlockPos start, end;
        if (tieE != null) {
            var tieDir = new Vector3d(0, 0, 1).mul(tieE.pose().basis()).normalize();
            var velDir = new Vector3d(velocity.x(), velocity.y(), velocity.z()).normalize();

            if (tieDir.dot(velDir) >= 0) { // Heading in positive direction
                trackVelocity = velocity.length();
                start = tie;
                end = tieE.nextPos();
                progress = 0;
            } else {
                trackVelocity = -velocity.length();
                start = tieE.prevPos();
                end = tie;
                progress = 1;
            }
        } else {
            return null;
        }

        var startE = TrackTiesBlockEntity.of(world, start);
        if (startE != null) {
            var follower = new TrackFollowerEntity(world);
            follower.trackVelocity = trackVelocity;
            follower.splinePieceProgress = progress;
            follower.setStretch(start, end);
            follower.setPos(startPos);
            follower.getEntityData().set(ORIENTATION, startE.pose().basis().getNormalizedRotation(new Quaternionf()));

            return follower;
        }

        return null;
    }

    public void setStretch(@Nullable BlockPos start, @Nullable BlockPos end) {
        this.startTie = start;
        this.endTie = end;
    }

    // For more accurate client side position interpolation, we can conveniently use the
    // same cubic hermite spline formula rather than linear interpolation like vanilla,
    // since we have not only the position but also its derivative (velocity)
    protected void interpPos(int step) {
        double t = 1 / (double)step;

        var clientPos = new Vector3d(this.getX(), this.getY(), this.getZ());

        var cv = this.getDeltaMovement();
        var clientVel = new Vector3d(cv.x(), cv.y(), cv.z());

        var newClientPos = new Vector3d();
        var newClientVel = new Vector3d();
        Pose.cubicHermiteSpline(t, 1, clientPos, clientVel, this.serverPosition, this.serverVelocity,
                newClientPos, newClientVel);

        this.setPos(newClientPos.x(), newClientPos.y(), newClientPos.z());
        this.setDeltaMovement(newClientVel.x(), newClientVel.y(), newClientVel.z());
    }

    @Override
    public void tick() {
        super.tick();

        var world = this.level();
        if (world.isClientSide()) {
            this.clientMotion = this.position().reverse();
            if (this.positionInterpSteps > 0) {
                this.interpPos(this.positionInterpSteps);
                this.positionInterpSteps--;
            } else {
                this.reapplyPosition();
                this.setDeltaMovement(this.serverVelocity.x(), this.serverVelocity.y(), this.serverVelocity.z());
            }
            this.clientMotion = this.clientMotion.add(this.position());

            this.lastClientOrientation.set(this.clientOrientation);
            if (this.oriInterpSteps > 0) {
                float delta = 1 / (float) oriInterpSteps;
                this.clientOrientation.slerp(this.getEntityData().get(ORIENTATION), delta);
                this.oriInterpSteps--;
            } else {
                this.clientOrientation.set(this.getEntityData().get(ORIENTATION));
            }
        } else {
            this.updateServer();
        }
    }

    public void getClientOrientation(Quaternionf q, float tickDelta) {
        this.lastClientOrientation.slerp(this.clientOrientation, tickDelta, q);
    }

    public Vec3 getClientMotion() {
        return this.clientMotion;
    }

    public Matrix3dc getServerBasis() {
        return this.basis;
    }

    public void destroy() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    private void flyOffTrack(Entity firstPassenger) {
        firstPassenger.stopRiding();

        var newVel = new Vector3d(0, 0, this.trackVelocity).mul(this.basis);
        firstPassenger.setDeltaMovement(newVel.x(), newVel.y(), newVel.z());
        this.destroy();
    }

    protected void updateServer() {
        for (var passenger : this.getPassengers()) {
            passenger.fallDistance = 0;
        }

        var passenger = this.getFirstPassenger();
        if (passenger != null) {
            if (!hadPassenger) {
                hadPassenger = true;
            } else {
                var world = this.level();
                var startE = TrackTiesBlockEntity.of(world, this.startTie);
                var endE = TrackTiesBlockEntity.of(world, this.endTie);
                if (startE == null || endE == null) {
                    this.destroy();
                    return;
                }

                this.splinePieceProgress += this.trackVelocity * this.motionScale;
                if (this.splinePieceProgress > 1) {
                    this.splinePieceProgress -= 1;

                    var nextE = endE.next();
                    if (nextE == null) {
                        this.flyOffTrack(passenger);
                        return;
                    } else {
                        this.setStretch(this.endTie, nextE.getBlockPos());
                        startE = endE;
                        endE = nextE;
                    }
                } else if (this.splinePieceProgress < 0) {
                    this.splinePieceProgress += 1;

                    var prevE = startE.prev();
                    if (prevE == null) {
                        this.flyOffTrack(passenger);
                        return;
                    } else {
                        this.setStretch(prevE.getBlockPos(), this.startTie);
                        endE = startE;
                        startE = prevE;
                    }
                }

                var pos = new Vector3d();
                var grad = new Vector3d(); // Change in position per change in spline progress
                startE.pose().interpolate(endE.pose(), this.splinePieceProgress, pos, this.basis, grad);

                this.setPos(pos.x(), pos.y(), pos.z());
                this.getEntityData().set(ORIENTATION, this.basis.getNormalizedRotation(new Quaternionf()));

                double gradLen = grad.length();
                if (gradLen != 0) {
                    this.motionScale = 1 / grad.length();
                }

                var ngrad = new Vector3d(grad).normalize();
                var gravity = -ngrad.y() * GRAVITY;

                double dt = this.trackVelocity * this.motionScale; // Change in spline progress per tick
                grad.mul(dt); // Change in position per tick (velocity)
                this.setDeltaMovement(grad.x(), grad.y(), grad.z());

                var passengerVel = passenger.getDeltaMovement();
                var push = new Vector3d(passengerVel.x(), 0.0, passengerVel.z());
                if (push.lengthSquared() > 0.0001) {
                    var forward = new Vector3d(0, 0, 1).mul(this.basis);

                    double linearPush = forward.dot(push) * 2.0;
                    this.trackVelocity += linearPush;
                    passenger.setDeltaMovement(Vec3.ZERO);
                }

                var gradeVec = new Vector3d(0, 1, 0).mul(this.basis);
                gradeVec.mul(1, 0, 1);
                int power = Math.max(startE.power(), endE.power());

                this.trackVelocity += gravity;
                this.trackVelocity = startE.nextType().motion.calculate(this.trackVelocity, gradeVec.length(), power);
            }
        } else {
            if (this.hadPassenger) {
                this.destroy();
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        if (this.firstPositionUpdate) {
            this.firstPositionUpdate = false;
            super.lerpTo(x, y, z, yaw, pitch, interpolationSteps);
        }

        this.serverPosition.set(x, y, z);
        this.positionInterpSteps = interpolationSteps + 2;
        this.absRotateTo(yaw, pitch);
    }

    // This method should be called updateTrackedVelocity, its usage is very similar to the above method
    @Override
    public void lerpMotion(double x, double y, double z) {
        this.serverVelocity.set(x, y, z);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction positionUpdater) {
        positionUpdater.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ORIENTATION, new Quaternionf().identity());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);

        if (data.equals(ORIENTATION)) {
            if (this.firstOriUpdate) {
                this.firstOriUpdate = false;
                this.clientOrientation.set(getEntityData().get(ORIENTATION));
                this.lastClientOrientation.set(this.clientOrientation);
            }
            this.oriInterpSteps = this.getType().updateInterval() + 2;
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.startTie = SUtil.getBlockPos(nbt, "start");
        this.endTie = SUtil.getBlockPos(nbt, "end");
        this.trackVelocity = nbt.getDouble("track_velocity");
        this.motionScale = nbt.getDouble("motion_scale");
        this.splinePieceProgress = nbt.getDouble("spline_piece_progress");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        SUtil.putBlockPos(nbt, this.startTie, "start");
        SUtil.putBlockPos(nbt, this.endTie, "end");
        nbt.putDouble("track_velocity", this.trackVelocity);
        nbt.putDouble("motion_scale", this.motionScale);
        nbt.putDouble("spline_piece_progress", this.splinePieceProgress);
    }
}
