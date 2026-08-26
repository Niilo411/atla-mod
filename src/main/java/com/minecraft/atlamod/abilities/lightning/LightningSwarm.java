package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Right / Lightning. A bolt for everything within ten blocks at once, in every
 * direction — but the current is SHARED, so the more it has to reach the less any
 * one target takes.
 *
 * That trade is the whole ability, and it makes it two different tools depending on
 * what you point it at: one target takes 25 (over twelve hearts, enough to end most
 * things outright), where a crowd of nine or more takes the floor of 5 each and is
 * softened rather than killed.
 *
 * Until "Unbroken Storm" is bought, at which point the sharing goes away entirely and
 * it becomes both at once over twice the ground. That is the most expensive upgrade in
 * the element, and it is priced for removing the ability's own drawback rather than
 * for adding a number to it.
 */
public class LightningSwarm implements ChargedAbility {

    /** The element's one-second wind-up. See Lightning.MINIMUM_CHARGE_TICKS. */
    @Override
    public int getChargeTicks() {
        return Lightning.MINIMUM_CHARGE_TICKS;
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Lightning.gather((ServerLevel) player.level(), player, ticksHeld, getChargeTicks());
    }

    /** Key of the upgrade that removes the falloff and doubles the reach. */
    public static final String UNBROKEN_STORM = "lightning_swarm_unbroken_storm";

    /** How far the swarm reaches, in blocks, in every direction. Doubled by the upgrade. */
    private static final double RADIUS = 10.0;

    /** What a single target takes when it is the only one. */
    private static final float BASE_DAMAGE = 25.0F;

    /** How much is taken off the damage for each target beyond the first. */
    private static final float FALLOFF = 2.5F;

    /** However many it hits, nothing takes less than this. */
    private static final float MINIMUM_DAMAGE = 5.0F;

    @Override
    public String getName() {
        return "Lightning Swarm";
    }

    /**
     * 150 ordinarily, 1000 once Unbroken Storm is bought.
     *
     * The upgrade removes the ability's own drawback, so the chi is what it is paid
     * for. Note 1000 is more than a new bender can hold at all — getMaxChi is
     * {@code 500 + level*100} — so an upgraded swarm is uncastable below level 5.
     * That is a gate rather than a bug, and the same one Fire Rain and Tsunami have:
     * anyone with 25 levels to spend on the upgrade is long past it.
     */
    @Override
    public int getChiCost(BendingData data) {
        return data.hasUpgrade(UNBROKEN_STORM) ? 1000 : 150;
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
    public java.util.List<AbilityUpgrade> getUpgrades() {
        return java.util.List.of(new AbilityUpgrade(
                UNBROKEN_STORM,
                "Unbroken Storm",
                "Every target takes the full 25, and the reach doubles to 20 blocks",
                25));
    }

    /** The reach, doubled by Unbroken Storm. */
    private static double radius(BendingData data) {
        return data.hasUpgrade(UNBROKEN_STORM) ? RADIUS * 2.0 : RADIUS;
    }

    /**
     * What each target takes, given how many there are.
     *
     * Unbroken Storm removes the sharing entirely: every target takes the full base
     * damage however many there are. That is a far bigger change than it looks — it
     * turns the ability from "one target hard OR a crowd softly" into both at once,
     * which is why it costs 25 levels rather than the 10 the stun's does.
     */
    private static float damageFor(BendingData data, int targets) {
        if (data.hasUpgrade(UNBROKEN_STORM)) return BASE_DAMAGE;

        return Math.max(MINIMUM_DAMAGE, BASE_DAMAGE - (FALLOFF * (targets - 1)));
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Gathered BEFORE anything is struck, because the damage depends on how many
        // there are — hitting them as they were found would make the first target's
        // damage depend on nothing and the last one's on everything.
        List<LivingEntity> targets = new ArrayList<>();

        double radius = radius(data);

        AABB box = new AABB(player.position(), player.position()).inflate(radius);
        for (Entity candidate : level.getEntities(player, box)) {
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.distanceToSqr(player) > radius * radius) continue;
            targets.add(living);
        }

        if (targets.isEmpty()) {
            // The cast still happened — chi is spent by the dispatcher before this
            // runs — so it should at least look like it did something.
            Lightning.spark(level, player.position().add(0.0, 1.0, 0.0), 30, 0.8);
            return;
        }

        float damage = Lightning.damage(data, damageFor(data, targets.size()));

        Lightning.crack(level, player.position(), 1.4F, 1.0F);

        for (LivingEntity target : targets) {
            Lightning.visualStrike(level, target.position());
            target.hurt(player.damageSources().indirectMagic(player, player), damage);
        }
    }
}
