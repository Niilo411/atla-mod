package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Defensive / Water. Held while standing in water to knit yourself back together.
 *
 * Strictly a water ability in the literal sense: it will not start on dry land, and
 * it ends the moment the bender wades out, rather than carrying on for free once the
 * condition that justified it has gone.
 */
public class WaterHeal implements ChanneledAbility {

    /** Regeneration I. */
    private static final int REGEN_LEVEL = 0;

    /**
     * Exactly one heal beat. Regeneration I heals on ticks where its remaining
     * duration divides by 50, and the check runs before the duration is decremented,
     * so an instance created at 50 heals immediately and then expires cleanly.
     *
     * Re-applying only once the previous instance has gone therefore reproduces
     * vanilla's rate exactly, and leaves at most 2.5 seconds of regeneration running
     * on after the channel stops.
     */
    private static final int REGEN_DURATION = 50;

    @Override
    public String getName() {
        return "Water heal";
    }

    /** Paid per second while channeling, not up front. */
    @Override
    public int getChiCost() {
        return 0;
    }

    /** Granted per second, not up front. */
    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond() {
        return 15;
    }

    @Override
    public int getXpPerSecond() {
        return 7;
    }

    /** Waterbending: standing in water means the supply check passes for free. */
    @Override
    public boolean requiresWater() {
        return true;
    }

    /** No healing on dry land. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (player.isInWater()) return true;

        player.displayClientMessage(
                Component.literal("§bYou must be in water to heal!"), true);
        return false;
    }

    /** Wading out ends it, rather than leaving it running on land. */
    @Override
    public boolean canContinue(ServerPlayer player, BendingData data) {
        return player.isInWater();
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        // Re-applied ONLY once the previous instance has expired, never on a timer and
        // never every tick. Re-adding replaces the instance and resets its counter, and
        // regeneration heals only on ticks where that counter comes round: refreshing
        // early moves the beat, and refreshing constantly gives a permanent regeneration
        // icon that never heals at all. Letting each instance run out keeps vanilla's rate.
        //
        // It also means a stronger regeneration the player already has — from a potion,
        // say — is left alone rather than being replaced with ours.
        if (player.getEffect(MobEffects.REGENERATION) == null) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, REGEN_DURATION, REGEN_LEVEL, false, true, true));
        }

        if (!(player.level() instanceof ServerLevel level)) return;

        // Water drawn up around the bender.
        level.sendParticles(ParticleTypes.BUBBLE_POP,
                player.getX(), player.getY() + 0.8, player.getZ(),
                4, 0.4, 0.6, 0.4, 0.02);

        if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.HEART,
                    player.getX(), player.getY() + 1.6, player.getZ(),
                    1, 0.3, 0.2, 0.3, 0.0);
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Regeneration is left to run out on its own rather than being stripped: it
        // may not be ours to take away, and REGEN_DURATION is short enough that it
        // fades on its own moments after the channel ends.
    }
}
