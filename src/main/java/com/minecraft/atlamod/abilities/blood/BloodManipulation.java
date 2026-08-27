package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Blood. Held: the target is a puppet. Wherever the bender points, they go.
 *
 * The element's masterpiece and its longest wait — 25 chi a second behind a fifty
 * second cooldown. Taking somebody's movement away entirely is the strongest control
 * in the mod, and the cooldown is what stops it simply being how every fight goes.
 *
 * The victim is picked ONCE, when the channel starts, and held until it ends — unlike
 * Blood Slow, which re-aims every tick. A puppet dropped by glancing away would be
 * unusable for the thing it exists to do.
 */
public class BloodManipulation implements ChanneledAbility {

    /** How far the grip reaches when first taken, in blocks. */
    private static final double REACH = 16.0;

    /** How far off the crosshair a target may be and still be caught. */
    private static final double TOLERANCE = 2.5;

    /** How far the puppet may be dragged from the bender before the grip breaks. */
    private static final double LEASH = 24.0;

    /** How fast the puppet is moved, in blocks per tick. */
    private static final double SPEED = 0.45;

    /** How far ahead of the bender's crosshair the puppet is steered. */
    private static final double AIM_REACH = 12.0;

    @Override
    public String getName() {
        return "Blood manipulation";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0; // Channels pay by the second.
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 25;
    }

    /** Zero: the xp goes into the BLOOD track instead, in onTick. */
    @Override
    public double getXpPerSecond() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 1000; // 50 seconds, and it starts when the grip ENDS
    }

    /** Refuses with nothing in front, or on somebody stronger. Costs nothing either way. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        return target != null && Blood.canBendOrTell(player, target);
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return;
        if (!Blood.canBend(player, target)) return;

        BloodPuppets.take(level, player, target);
        Blood.squelch(level, player.position(), 1.2F, 0.5F);
    }

    /**
     * Drives the puppet toward the bender's crosshair.
     *
     * Done here rather than in a ticking tracker because the grip lasts exactly as long
     * as the channel does — there is nothing to outlive the key being held, which is
     * the whole reason the other bloodbending abilities needed trackers and this one
     * does not.
     */
    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity puppet = BloodPuppets.of(player);
        if (puppet == null || !puppet.isAlive()) return;

        // Dragged too far, or into another level: the grip breaks rather than
        // stretching across the world.
        if (puppet.level() != level || puppet.distanceToSqr(player) > LEASH * LEASH) {
            BloodPuppets.release(player);
            return;
        }

        Vec3 wanted = player.getEyePosition().add(player.getLookAngle().scale(AIM_REACH));
        Vec3 toward = wanted.subtract(puppet.position());

        // Moved TOWARDS the crosshair at a capped speed rather than snapped to it, the
        // same call Tornado and Lightning ball make: flicking the view should drive the
        // puppet, not teleport it across the field in a single tick.
        if (toward.lengthSqr() > SPEED * SPEED) {
            toward = toward.normalize().scale(SPEED);
        }

        puppet.setDeltaMovement(toward.x, toward.y, toward.z);

        // A mob would otherwise keep walking wherever its own pathfinding wanted.
        if (puppet instanceof Mob mob) {
            mob.getNavigation().stop();
        }

        // A player's client owns their position and ignores server-side velocity
        // unless it is explicitly pushed to them.
        if (puppet instanceof Player) {
            puppet.hurtMarked = true;
        }

        // Being flown around by somebody else is not a fall the puppet chose.
        puppet.fallDistance = 0.0F;
        Blood.wrench(level, puppet, 3);

        if (data.getChannelTicks() % 20 == 0) {
            Blood.grantXp(player, data, 5);
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        BloodPuppets.release(player);
    }
}
