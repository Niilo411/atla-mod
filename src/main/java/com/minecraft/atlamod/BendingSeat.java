package com.minecraft.atlamod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible thing a bender rides while an ability is carrying them — Air Scooter
 * and Water Surf both.
 *
 * Riding is not a flourish, it is the mechanism. A passenger is carried by its
 * vehicle, so the server can steer the ride without ever contradicting the client
 * about where the player is — no rubber-banding — and the player's own WASD is simply
 * not consulted, which is what "you go where you look" requires.
 *
 * It deliberately does NOT override getControllingPassenger: the rider steers by
 * looking, which Rides reads, and has no direct control over the seat.
 */
public class BendingSeat extends Entity {

    /**
     * Whether the rider is rendered seated.
     *
     * Synched because the answer is needed on the CLIENT — LivingEntityRenderer asks
     * the vehicle, not the passenger, when it decides which pose to draw. An
     * airbender sits on their ball of air; a waterbender rides the surface standing up.
     */
    private static final EntityDataAccessor<Boolean> SEATED =
            SynchedEntityData.defineId(BendingSeat.class, EntityDataSerializers.BOOLEAN);

    /**
     * Exactly a player's box, and that is the point.
     *
     * The seat is what collides with the world on the rider's behalf — passengers do
     * not collide themselves — so without a real box a ride would go straight through
     * walls. Matching the player's own 0.6 by 1.8 means it can only go where the rider
     * would fit anyway: a shorter box would happily carry them into a one-block gap
     * and suffocate them against the ceiling.
     */
    public static final EntityDimensions SIZE = EntityDimensions.fixed(0.6F, 1.8F);

    public BendingSeat(EntityType<? extends BendingSeat> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
    }

    /** Set before the rider mounts, so the client has it when it first draws them. */
    public void setSeated(boolean seated) {
        this.entityData.set(SEATED, seated);
    }

    @Override
    public boolean shouldRiderSit() {
        return this.entityData.get(SEATED);
    }

    /**
     * The rider sits exactly where the seat is, rather than at the default height
     * above it, so positioning the seat positions the player.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    /**
     * NEVER written to the chunk.
     *
     * This is the whole answer to the orphan problem. A seat exists only inside one
     * session's Rides map; if the server crashes, or a chunk unloads at exactly the
     * wrong moment, there is nothing on disk to come back as an invisible
     * passenger-less entity that no code remembers owning.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Nothing to persist — see shouldBeSaved. */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    /** Nothing to persist — see shouldBeSaved. */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SEATED, true);
    }

    /** Not something to shoot, hit, or bump into — only to ride. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Driven entirely by Rides from the server tick, so this does nothing.
     *
     * In particular it must NOT fall: a seat that ticked normally would drop out from
     * under its rider between the steering updates.
     */
    @Override
    public void tick() {
        // intentionally empty
    }

    /**
     * Losing the rider ends the seat, whatever the reason — including a player
     * shifting off under their own steam, which vanilla handles without asking us.
     */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);

        // isRemoved guards against re-entry: discarding a vehicle makes vanilla eject
        // its passengers, which lands straight back here.
        if (!this.level().isClientSide && !this.isRemoved()) {
            this.discard();
        }
    }
}
