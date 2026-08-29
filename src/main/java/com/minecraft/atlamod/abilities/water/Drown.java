package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Water. Takes the air out of something and keeps it out.
 *
 * Charged, and it fires on release like Fire blow does — a second of holding is a
 * real cast, five is the most it can be. The bubbles go at any charge; what grows is
 * how long the victim is kept without air afterwards.
 *
 * However long that is, it ends early if the bender loses sight of them: the grip has
 * to be maintained, so breaking line of sight is the counter-play. See Drownings.
 *
 * The drowning itself is driven by Drownings rather than left to vanilla, because
 * vanilla only drowns what is underwater and refills its air the moment it is not.
 */
public class Drown implements ChargedAbility {

    /** Five seconds to reach full strength. */
    private static final int MAX_CHARGE = 100;

    /** A second is the shortest cast that counts; below it nothing is spent. */
    private static final int MIN_CHARGE = 20;

    /** Drowning lasts this long at a one second charge, and this long at five. */
    private static final int MIN_DROWN = 100;   // 5 seconds
    private static final int MAX_DROWN = 300;   // 15 seconds

    /** How far away a victim can be picked out. */
    private static final double REACH = 20.0;

    /** How near the line of sight something has to be to count as the target. */
    private static final double AIM_TOLERANCE = 1.5;

    @Override
    public String getName() {
        return "Drown";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 250;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds
    }

    @Override
    public int getChargeTicks() {
        return MAX_CHARGE;
    }

    /** Letting go early still casts it, just for less. */
    @Override
    public boolean firesOnRelease() {
        return true;
    }

    @Override
    public int getMinimumChargeTicks() {
        return MIN_CHARGE;
    }

    /** Waterbending: free near open water, otherwise a unit from the canteen. */
    @Override
    public boolean requiresWater() {
        return true;
    }

    /**
     * Needs something to drown, checked both when the charge starts and again when it
     * lands — the dispatcher runs this before spending anything, so a victim who
     * breaks line of sight during the wind-up costs the bender nothing.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (findVictim(player) != null) return true;

        player.displayClientMessage(
                Component.literal("§bNo target in sight!"), true);
        return false;
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Water gathering at the bender's hands, and around the victim's head so they
        // can see it coming.
        float power = Math.min(1.0F, ticksHeld / (float) MAX_CHARGE);

        Vec3 look = player.getLookAngle();
        level.sendParticles(ParticleTypes.BUBBLE,
                player.getX() + look.x, player.getEyeY(), player.getZ() + look.z,
                2 + (int) (6 * power), 0.3, 0.3, 0.3, 0.01);

        LivingEntity victim = findVictim(player);
        if (victim != null && ticksHeld % 4 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE,
                    victim.getX(), victim.getEyeY(), victim.getZ(),
                    3 + (int) (8 * power), 0.3, 0.3, 0.3, 0.02);
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        LivingEntity victim = findVictim(player);
        if (victim == null) return;

        // How far the charge got, recorded by the dispatcher just before the cast.
        int charged = Math.min(MAX_CHARGE, data.getLastChargeTicks());
        float power = (float) (charged - MIN_CHARGE) / (float) (MAX_CHARGE - MIN_CHARGE);
        power = Math.max(0.0F, Math.min(1.0F, power));

        int duration = MIN_DROWN + Math.round((MAX_DROWN - MIN_DROWN) * power);

        // Held only while the bender can still see them — see Drownings.
        Drownings.start(player, victim, duration);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 40, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, victim.blockPosition(), SoundEvents.AMBIENT_UNDERWATER_ENTER,
                    SoundSource.PLAYERS, 1.2F, 1.4F);
        }
    }

    /** The nearest living thing along the bender's line of sight. See Aiming. */
    private static LivingEntity findVictim(ServerPlayer player) {
        return com.minecraft.atlamod.abilities.Aiming.nearestAlongLook(player, REACH, AIM_TOLERANCE);
    }
}
