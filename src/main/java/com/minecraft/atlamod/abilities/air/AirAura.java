package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Defensive / Air. A held shell of racing wind. Where Fire Shield and Water Shield
 * stop everything, this one is a filter: arrows, tridents, fireballs and anything
 * else thrown at the bender are turned aside, and so is the ground — but a sword is
 * not. Getting close enough to swing is how you beat it.
 *
 * That is also what makes it cheap. 5 chi a second against the shields' 25 buys a
 * defence with a hole in it. Buffeting Wind closes the hole, unpins the bender, and
 * doubles the rate to 10 — the aura is paid for at what it is currently worth.
 *
 * Which damage it stops lives in blocks(); the dispatcher consults that through
 * AbilityHandler#blocksDamage.
 */
public class AirAura implements ChanneledAbility {

    /** Buys melee protection, and with it the freedom to walk while it is up. */
    public static final String BUFFETING_WIND = "air_aura_buffeting_wind";
    private static final int BUFFETING_WIND_COST = 10;

    /** How many points make up each spinning ring. */
    private static final int RING_POINTS = 12;

    /** Ring radius in blocks. */
    private static final double RADIUS = 1.1;

    /** Ticks for the rings to complete one full rotation. Faster than Fire Shield's. */
    private static final double SPIN_PERIOD = 20.0;

    /** Chi a second before Buffeting Wind, and after it. */
    private static final int BASE_CHI_PER_SECOND = 5;
    private static final int UPGRADED_CHI_PER_SECOND = 10;

    @Override
    public String getName() {
        return "Air Aura";
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

    /**
     * Doubles once Buffeting Wind is bought.
     *
     * The upgrade turns a filter into a proper shield you can walk around inside, so
     * it costs what that is worth rather than being free forever after one purchase.
     * XP is unchanged at 1/sec: the rate is what the stronger aura costs, not what
     * holding it up teaches you.
     */
    @Override
    public int getChiPerSecond(BendingData data) {
        return data.hasUpgrade(BUFFETING_WIND) ? UPGRADED_CHI_PER_SECOND : BASE_CHI_PER_SECOND;
    }

    @Override
    public double getXpPerSecond() {
        return 1;
    }

    @Override
    public java.util.List<AbilityUpgrade> getUpgrades() {
        return java.util.List.of(new AbilityUpgrade(
                BUFFETING_WIND,
                "Buffeting Wind",
                "Also turns melee aside and frees you to move, for 10 chi/sec",
                BUFFETING_WIND_COST));
    }

    /**
     * What the wind actually turns aside.
     *
     * Projectiles always — that is the whole ability. Fall damage too: a bender
     * wrapped in a cushion of air does not break their legs, and it means the aura
     * can be thrown up on the way down. Melee only once Buffeting Wind is bought.
     *
     * Everything else — fire, lava, drowning, suffocation, explosions, poison —
     * lands normally. This is a shell against things aimed at you, not a bubble.
     */
    @Override
    public boolean blocks(BendingData data, DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return true;
        if (source.is(DamageTypeTags.IS_FALL)) return true;

        return data.hasUpgrade(BUFFETING_WIND) && isMelee(source);
    }

    /**
     * Holds the bender still — until Buffeting Wind, which frees them to walk.
     *
     * Rooting is the price of a defence you can hold up indefinitely for almost no
     * chi. Once the aura also stops swords it would be far too strong to walk around
     * inside as well, so the upgrade trades the hole in the defence for the ability
     * to move, rather than simply adding to it.
     */
    @Override
    public boolean rootsPlayer(BendingData data) {
        return !data.hasUpgrade(BUFFETING_WIND);
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.9F, 1.2F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        double cx = player.getX();
        double cy = player.getY();
        double cz = player.getZ();

        // Three rings at ankle, waist and head height, turning fast enough to read as
        // a moving wall of air rather than as decoration sitting around the player.
        double spin = (player.tickCount % SPIN_PERIOD) / SPIN_PERIOD * Math.PI * 2.0;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = spin + (Math.PI * 2.0 * i / RING_POINTS);
            double px = cx + Math.cos(angle) * RADIUS;
            double pz = cz + Math.sin(angle) * RADIUS;

            level.sendParticles(ParticleTypes.CLOUD, px, cy + 0.15, pz, 1, 0.0, 0.02, 0.0, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, px, cy + 0.95, pz, 1, 0.0, 0.02, 0.0, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, px, cy + 1.75, pz, 1, 0.0, 0.02, 0.0, 0.0);
        }

        // A gust curling through the middle, once every half second. Kept rationed:
        // a directed particle is one packet each, so this is the expensive kind.
        if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.SMALL_GUST, cx, cy + 1.0, cz, 2, 0.5, 0.6, 0.5, 0.0);
        }

        // Once Buffeting Wind is up the shell is denser, so it looks like the
        // stronger thing it now is.
        if (data.hasUpgrade(BUFFETING_WIND)) {
            level.sendParticles(ParticleTypes.CLOUD, cx, cy + 1.0, cz, 3, 0.6, 0.8, 0.6, 0.02);
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    25, 0.7, 0.9, 0.7, 0.15);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /**
     * A blow struck by something standing next to you.
     *
     * Defined as "has an attacker behind it and did not fly there": that covers a
     * player's sword, a zombie's fists and a thorns riposte, while explosions — which
     * also carry an attacker — are left out, since being sheltered from an arrow is
     * no reason to be sheltered from TNT.
     */
    private static boolean isMelee(DamageSource source) {
        return source.getEntity() != null
                && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypeTags.IS_EXPLOSION);
    }
}
