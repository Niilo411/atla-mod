package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilitySupport;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * Air / CENTRE. Held: the bender sits still and gathers experience far faster than
 * ordinary meditation does, and the further along they already are the faster it goes.
 *
 * The first ability in the mod that belongs to NO path. It sits in the middle of the
 * four arms and is bought outright for 20 levels, so it is available whichever way a
 * bender has gone — which is the point of it: it is not a technique, it is practice.
 *
 * The rate is the whole ability, and it rewards a bender who already has levels rather
 * than one who needs them. Ordinary meditation is a flat 2 a second at any level; this
 * starts there and steps up by 2 every ten levels — 4 at level 10, 6 at 20, 8 at 30 —
 * capping at 10 from level 40 until Pure peace takes the ceiling off.
 */
public class AdvancedMeditating implements ChanneledAbility {

    /** Key of the upgrade that lifts the ceiling off the rate. */
    public static final String PURE_PEACE = "advanced_meditating_pure_peace";

    /**
     * What the rate starts at, before any levels are counted.
     *
     * Two a second, which is exactly what ordinary meditation already gives — so this
     * is never worse than the thing it improves on. That matters more than it looks:
     * buying this costs 20 LEVELS, which are SPENT, so a bender can be sitting at level
     * 0 the moment they unlock it. A rate that started from nothing would mean paying
     * twenty levels for an ability that gathered nothing at all.
     */
    private static final int BASE = 2;

    /** How many levels buy each step up. */
    private static final int LEVELS_PER_STEP = 10;

    /** How much a step is worth. */
    private static final int PER_STEP = 2;

    /** The ceiling, until Pure peace is bought. Reached at level 40. */
    private static final int CAP = 10;

    @Override
    public String getName() {
        return "Advanced meditating";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    /** Free. What it costs is standing still, not chi. */
    @Override
    public int getChiPerSecond(BendingData data) {
        return 0;
    }

    /**
     * Zero, deliberately: the rate depends on the bender's LEVEL, and this method is
     * not given the data to work that out. The xp is granted in onTick instead, the
     * same way every bloodbending ability grants into its own track.
     */
    @Override
    public double getXpPerSecond() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    /**
     * Held still while it runs, exactly as ordinary meditation is.
     *
     * Both halves, since rooting server-side alone leaves the client walking and being
     * corrected every tick. That stillness IS the cost of the ability — there is no
     * chi price, so being unable to do anything else is the whole of what it asks.
     */
    @Override
    public boolean rootsPlayer(BendingData data) {
        return true;
    }

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(new AbilityUpgrade(
                PURE_PEACE,
                "Pure peace",
                "No ceiling on the rate — it keeps climbing with your level",
                25));
    }

    /**
     * What this bender gathers per second, at their current level.
     *
     * Public because it is worth being able to ask from outside — and because the
     * formula is the ability, so it belongs somewhere it can be read rather than
     * buried in the tick.
     */
    public static int rateFor(BendingData data) {
        // Integer division on purpose: the design says "every ten levels", so the rate
        // STEPS at each boundary rather than creeping up level by level. 2 a second up
        // to level 9, 4 from 10, 6 from 20, 8 from 30, 10 from 40.
        int steps = data.getLevel() / LEVELS_PER_STEP;
        int rate = BASE + (steps * PER_STEP);

        if (!data.hasUpgrade(PURE_PEACE)) {
            rate = Math.min(CAP, rate);
        }

        return rate;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Drawn every few ticks rather than every one: this runs for as long as the
        // bender cares to sit there, and a particle call twenty times a second for
        // minutes on end is pure noise on the wire.
        if (data.getChannelTicks() % 4 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.2, player.getZ(),
                    6, 0.5, 0.6, 0.5, 0.08);
        }

        if (data.getChannelTicks() % 20 != 0) return;

        // Granted through AbilitySupport so the level roll-over is handled in the one
        // place that knows how, rather than being reimplemented here.
        AbilitySupport.grantXp(data, rateFor(data));
        AbilitySupport.syncData(player, data);

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.25F, 1.6F);
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Nothing to put away: the ability is only ever particles and xp.
    }
}
