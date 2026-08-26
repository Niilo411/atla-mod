package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Right / Sound. A shriek at everything in front: twenty-five seconds unable to hear
 * anything at all, and ten seconds unable to bend.
 *
 * The bending lockout is the real weapon and the deafness is the flavour -- ten
 * seconds is a very long time to be unable to answer, which is why it costs 200 chi
 * and waits a hundred seconds. The caster is never caught by their own shriek.
 *
 * Two different mechanisms because they run for two different lengths: the deafness
 * is a MobEffect (see DeafenedEffect, silenced client-side), and the lockout is a
 * counter on BendingData that AbilityHandler checks before anything is spent.
 */
public class Deafen implements Ability {

    /** How far in front the shriek reaches, in blocks. */
    private static final double REACH = 20.0;

    /**
     * Cone width, as the minimum dot product with the look vector.
     *
     * 0.4 is about 66 degrees -- the mod's "everything on your screen" figure, shared
     * with Wind and Earth trap. Deliberately wider than the real view frustum, so
     * something at the very edge of the screen is caught rather than feeling unfairly
     * missed.
     */
    private static final double CONE_DOT = 0.4;

    /** Twenty-five seconds of silence. */
    private static final int DEAF_TICKS = 500;

    /** Ten seconds of being unable to bend. */
    private static final int LOCKOUT_TICKS = 200;

    @Override
    public String getName() {
        return "Deafen";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 200;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    @Override
    public int getCooldownTicks() {
        return 2000; // 100 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        int deaf = Sound.duration(data, DEAF_TICKS);
        int lockout = Sound.duration(data, LOCKOUT_TICKS);

        // The shared "everything on screen" sweep: a cone plus a line-of-sight test,
        // caster always excluded. That exclusion is what keeps the bender's own ears
        // and bending intact, exactly as the design asks.
        for (LivingEntity caught : Aiming.allInSight(player, REACH, CONE_DOT)) {
            caught.addEffect(new MobEffectInstance(
                    ModEffects.DEAFENED, deaf, 0, false, true, true));

            // The lockout only means anything to something that bends.
            if (caught instanceof ServerPlayer victim) {
                BendingData victimData = victim.getData(ModAttachments.BENDING_DATA);
                victimData.setBendingLockedTicks(lockout);
                victim.setData(ModAttachments.BENDING_DATA, victimData);
            }

            Sound.burst(level, caught.getEyePosition(), 12, 0.4);
        }

        Sound.shriek(level, player.position(), 2.0F);
        Sound.burst(level, player.getEyePosition(), 30, 0.6);
    }
}
