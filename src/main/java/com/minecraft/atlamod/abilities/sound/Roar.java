package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Right / Sound. A roar in every direction: everything within fifteen blocks is left
 * Disoriented, with no regard for where the bender is facing.
 *
 * No damage at all -- pure control, like Water push and Air pull. What it has over
 * those is that it does not need aiming: a roar catches what is behind you as readily
 * as what is in front, which is what makes it the answer to being surrounded.
 */
public class Roar implements Ability {

    /** How far the roar carries, in blocks, in every direction. */
    private static final double RADIUS = 15.0;

    /** How long the disorientation holds. */
    private static final int DISORIENT_TICKS = 200; // 10 seconds

    @Override
    public String getName() {
        return "Roar";
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
        return 500; // 25 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Sound boosting lengthens it, like every other air and sound effect.
        int ticks = Sound.duration(data, DISORIENT_TICKS);

        AABB area = new AABB(player.position(), player.position()).inflate(RADIUS);

        // getEntities(player, ...) skips the caster, which is right here: a bender is
        // not disoriented by their own voice.
        for (Entity caught : level.getEntities(player, area)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.position().distanceToSqr(player.position()) > RADIUS * RADIUS) continue;

            living.addEffect(new MobEffectInstance(
                    ModEffects.DISORIENTATION, ticks, 0, false, true, true));
        }

        // Drawn as rings running outward rather than one burst, so the roar visibly
        // travels the fifteen blocks it reaches.
        for (int r = 2; r <= RADIUS; r += 3) {
            Sound.ring(level, player.position(), r, 12 + r);
        }
        Sound.boom(level, player.position(), 1.6F);
    }
}
