package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Blood. Held: blood is pulled out of the target and into the bender — two
 * hearts a second taken, two hearts a second healed.
 *
 * The most expensive channel in the mod at 100 chi a second, and it has to be: a
 * straight trade of somebody else's health for your own has no ceiling other than what
 * it costs to keep running. A full pool buys about six seconds of it.
 *
 * Healed directly rather than through Regeneration, deliberately. Regeneration heals on
 * its own internal beat and re-applying it every tick breaks it outright — the trap
 * Water heal documents — and a trade like this should land exactly when the damage does
 * rather than on a timer of its own.
 */
public class BloodSuck implements ChanneledAbility {

    /** How far the pull reaches, in blocks. */
    private static final double REACH = 16.0;

    /** How far off the crosshair a target may be and still be caught. */
    private static final double TOLERANCE = 2.0;

    /** Two hearts a second, as specced — taken from them and given to the bender. */
    private static final float PER_SECOND = 4.0F;

    @Override
    public String getName() {
        return "Blood suck";
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
        return 100;
    }

    /** Zero: the xp goes into the BLOOD track instead, in onTick. */
    @Override
    public double getXpPerSecond() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 200; // 10 seconds, and it starts when the pull ENDS
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        Blood.squelch((ServerLevel) player.level(), player.position(), 1.0F, 0.6F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return;
        if (!Blood.canBend(player, target)) return;

        // The stream between them, drawn every tick so the pull is visible even
        // between the beats on which it actually lands.
        drawStream(level, player, target);

        // On a one-second beat rather than per tick, so the figures mean what they say
        // and are not silently thinned by invulnerability frames.
        if (data.getChannelTicks() % 20 != 0) return;

        target.hurt(player.damageSources().indirectMagic(player, player),
                Blood.damage(data, PER_SECOND));

        player.heal(PER_SECOND);

        Blood.wrench(level, target, 12);
        Blood.grantXp(player, data, 10);
    }

    /** The red thread running from the target back to the bender. */
    private static void drawStream(ServerLevel level, ServerPlayer player, LivingEntity target) {
        Vec3 from = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        Vec3 to = player.getEyePosition();

        Vec3 along = to.subtract(from);
        int steps = (int) Math.max(1, along.length() * 2);

        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(along.scale(i / (double) steps));
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Nothing to put away: the stream is only ever particles.
    }
}
