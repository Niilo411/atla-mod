package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import com.minecraft.atlamod.abilities.earth.EarthGrabs;
import com.minecraft.atlamod.abilities.earth.EarthWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Metal. Three seconds of gathering, then three walls of ground sent rolling
 * AWAY down the crosshair, one per left click.
 *
 * Earth grab in reverse, and built out of exactly the same wave: {@link EarthGrabs} is
 * a moving body of slices that travels rather than leaving a wall behind, and it
 * carries whatever it passes over in its own direction of travel. Earth grab points it
 * home; this points it out.
 *
 * That is what the ability is FOR. It is not a projectile that happens to be
 * wall-shaped — it is a wall of real ground travelling twenty blocks with everything
 * in its path shoved along in front of it, which is a very different thing to be hit
 * by.
 *
 * Both held shapes at once, like Fireball and Air splinters: the charge builds the
 * three walls, and the armed slot spends them a click at a time.
 */
public class StoneWalls implements ChargedAbility, TwoPhaseAbility {

    /** How many walls a full charge produces. */
    private static final int WALLS = 3;

    /** 6 hearts, as specced. */
    private static final float DAMAGE = 12.0F;

    /** How far out the wall starts and how far it travels, in blocks. */
    private static final int FROM = 2;
    private static final int TO = 20;

    /** How wide the damage sweep is, matching the wave's own footprint. */
    private static final double HALF_WIDTH = 3.5;

    @Override
    public String getName() {
        return "Stone walls";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    /** One second, and it waits for the LAST of the three. */
    @Override
    public int getCooldownTicks() {
        return 20;
    }

    @Override
    public int getChargeTicks() {
        return 60; // 3 seconds
    }

    /** Three clicks before the slot is spent. */
    @Override
    public int getShots() {
        return WALLS;
    }

    /** Held until thrown: the walls wait as long as the bender likes. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 ahead = player.position().add(player.getLookAngle().scale(2.0));

        Metal.spark(level, ahead, 3 + (int) (progress * 8), 1.4 - (progress * 0.9));
    }

    /** The walls waiting, drawn as slabs turning in front of the bender. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 centre = player.getEyePosition().add(player.getLookAngle().scale(1.6));

        double phase = player.tickCount * 0.15;
        for (int i = 0; i < data.getTwoPhaseShots(); i++) {
            double a = phase + (i * Math.PI * 2.0 / WALLS);
            Vec3 at = centre.add(Math.cos(a) * 1.0, Math.sin(a * 1.3) * 0.3, Math.sin(a) * 1.0);

            Metal.spark(level, at, 2, 0.2);
        }
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 look = player.getLookAngle();

        // Flattened: a wall follows the ground, so the pitch aims where it goes rather
        // than tipping it into the sky.
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) heading = new Vec3(0.0, 0.0, 1.0);
        heading = heading.normalize();

        BlockState material = materialUnder(level, player.position().add(heading.scale(FROM)));

        // Outward: from close to far, which is the reverse of Earth grab's own launch
        // and is the whole ability.
        EarthGrabs.launch(player, player.position(), heading, material, FROM, TO);

        // The wave itself only shoves; the damage is dealt once, up front, to whatever
        // is standing in the corridor it is about to cross. Hitting per tick as it
        // travelled would multiply six hearts by however long the wall took.
        strike(player, data, level, heading);

        Metal.clang(level, player.position(), 1.1F, 1.0F);
    }

    /** Everything in the wall's path takes the blow, once. */
    private static void strike(ServerPlayer player, BendingData data,
                               ServerLevel level, Vec3 heading) {
        Vec3 across = new Vec3(-heading.z, 0.0, heading.x);
        float damage = Metal.damage(data, DAMAGE);

        AABB search = new AABB(player.position(), player.position()).inflate(TO);

        for (Entity candidate : level.getEntities(player, search)) {
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 offset = living.position().subtract(player.position());

            double along = offset.dot(heading);
            if (along < FROM || along > TO) continue;
            if (Math.abs(offset.dot(across)) > HALF_WIDTH) continue;
            if (Math.abs(offset.y) > 4.0) continue;

            living.hurt(player.damageSources().indirectMagic(player, player), damage);
        }
    }

    /**
     * What the wall should be made of.
     *
     * EarthWorks.materialFor is reused whole: it mirrors the surface so a wall out of
     * a hillside looks like the hillside, swaps anything that FALLS for dirt so the
     * wall does not collapse as it goes up, and falls back to dirt for anything that
     * is not plain diggable ground.
     */
    private static BlockState materialUnder(ServerLevel level, Vec3 at) {
        BlockPos ground = EarthWorks.surfaceUnder(level, BlockPos.containing(at), 3, 4);
        if (ground == null) return Blocks.STONE.defaultBlockState();

        return EarthWorks.materialFor(level, ground);
    }

    /** Arming is the whole cast: the walls gather and wait on the clicks. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Metal.scrape((ServerLevel) player.level(), player.position(), 0.9F, 0.8F);
    }
}
