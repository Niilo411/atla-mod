package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lightning. The bender becomes the bolt: fifteen blocks along the crosshair
 * in an instant, with lightning striking the ground every two blocks of the way and
 * stopping two blocks short of where they land.
 *
 * A teleport rather than a launch, which is the whole character of it — Fire Leap
 * and Air jump both throw the bender and let physics do the rest, where this one
 * simply arrives.
 */
public class LightningJump implements ChargedAbility {

    /**
     * The element's one-second wind-up. Nothing in lightningbending fires on the
     * press — see Lightning.MINIMUM_CHARGE_TICKS.
     */
    @Override
    public int getChargeTicks() {
        return Lightning.MINIMUM_CHARGE_TICKS;
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Lightning.gather((ServerLevel) player.level(), player, ticksHeld, getChargeTicks());
    }

    /** How far the jump carries, in blocks. */
    private static final double RANGE = 15.0;

    /** A bolt every this many blocks along the path. */
    private static final int STRIKE_EVERY = 2;

    /**
     * How far short of the destination the bolts stop, in blocks.
     *
     * The design asks for this explicitly, and it is not decoration: the bolts are
     * visual-only, but landing one on the square the bender is about to occupy reads
     * as though they were struck by their own ability.
     */
    private static final double STRIKE_STOPS_SHORT = 2.0;

    /** How far up and down a landing spot is searched for, in blocks. */
    private static final int GROUND_SCAN = 4;

    @Override
    public String getName() {
        return "Lightning Jump";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 300; // 15 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.position();
        Vec3 look = player.getLookAngle();

        // Flat aim: a jump that followed the pitch would bury the bender in the floor
        // when they looked down and fling them at the sky when they looked up. The
        // vertical part is handled by the landing search instead, which is what lets
        // it climb a hill or drop off a ledge.
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) heading = new Vec3(0.0, 0.0, 1.0);
        heading = heading.normalize();

        Vec3 destination = findLanding(level, from, heading);

        // The trail of bolts, laid BEFORE the move so they run away from where the
        // bender was standing rather than trailing behind where they now are.
        double trail = destination.subtract(from).horizontalDistance() - STRIKE_STOPS_SHORT;
        for (double d = STRIKE_EVERY; d <= trail; d += STRIKE_EVERY) {
            Vec3 along = from.add(heading.scale(d));
            Vec3 ground = groundAt(level, along);
            Lightning.visualStrike(level, ground);
        }

        Lightning.spark(level, from.add(0.0, 1.0, 0.0), 40, 0.6);
        Lightning.crack(level, from, 1.0F, 1.4F);

        player.teleportTo(destination.x, destination.y, destination.z);

        // The arrival. Momentum is dropped entirely: the bender did not travel here
        // so much as reappear, and carrying their old velocity through would send
        // them skidding onward.
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        // A teleport is not a fall, so whatever height was banked on the way is not
        // theirs to pay for.
        player.fallDistance = 0.0F;

        Lightning.spark(level, destination.add(0.0, 1.0, 0.0), 40, 0.6);
        Lightning.crack(level, destination, 1.0F, 1.2F);
    }

    /**
     * Walks the path a block at a time and returns the last place the bender could
     * actually stand.
     *
     * Stepping rather than teleporting straight to the far end is what stops the
     * ability being a way through walls: the walk stops at the first square that is
     * blocked, so a jump into a cliff face puts the bender against it rather than
     * inside it or on the other side.
     */
    private Vec3 findLanding(ServerLevel level, Vec3 from, Vec3 heading) {
        Vec3 best = from;

        for (double d = 1.0; d <= RANGE; d += 1.0) {
            Vec3 candidate = groundAt(level, from.add(heading.scale(d)));
            if (candidate == null) break;
            best = candidate;
        }

        return best;
    }

    /**
     * The standable spot nearest the given position, searched down first and then up.
     *
     * Returns null when there is nowhere to stand within the scan, which the walk
     * above treats as the end of the road.
     */
    private Vec3 groundAt(ServerLevel level, Vec3 near) {
        BlockPos origin = BlockPos.containing(near);

        for (int dy = 0; dy >= -GROUND_SCAN; dy--) {
            BlockPos at = origin.offset(0, dy, 0);
            if (standable(level, at)) return Vec3.atBottomCenterOf(at);
        }
        for (int dy = 1; dy <= GROUND_SCAN; dy++) {
            BlockPos at = origin.offset(0, dy, 0);
            if (standable(level, at)) return Vec3.atBottomCenterOf(at);
        }

        return null;
    }

    /** Two blocks of clear air with something solid underneath — room for a player. */
    private boolean standable(ServerLevel level, BlockPos at) {
        return level.getBlockState(at).isAir()
                && level.getBlockState(at.above()).isAir()
                && !level.getBlockState(at.below()).isAir();
    }
}
