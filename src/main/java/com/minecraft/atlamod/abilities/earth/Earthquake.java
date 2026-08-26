package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.network.EarthquakePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Earth. The ground goes out from under everything for twenty blocks in
 * every direction, and stays out for half a minute.
 *
 * No damage and no displacement — it is thirty seconds of Slowness and Disorientation
 * on everything nearby, which for that long is a fight already decided. The bender is
 * the one thing it spares.
 *
 * They do NOT get to stand there calmly, though: the camera shake reaches them too. It
 * is their earthquake, so the effects know to leave them alone, but the ground under
 * their feet does not.
 */
public class Earthquake implements Ability {

    /** How far the shaking reaches, in every direction. */
    private static final double RADIUS = 20.0;

    /** Half a minute of it. */
    private static final int DURATION = 600;

    /**
     * How long the camera shakes, which is deliberately NOT the whole thirty seconds.
     *
     * The effects are the ability; the shake is the moment it lands. Half a minute of
     * a moving view is a headache rather than a spectacle, so it runs for the first
     * five seconds and stops while the Slowness and Disorientation carry on.
     */
    private static final int SHAKE_DURATION = 100;

    /** Slowness II — enough to make crossing the ground a real problem. */
    private static final int SLOWNESS_LEVEL = 1;

    @Override
    public String getName() {
        return "Earthquake";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
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
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 centre = player.position();

        // A box of +-RADIUS around the caster; the round check below trims it to the
        // sphere the ability actually describes.
        AABB search = new AABB(centre, centre).inflate(RADIUS);

        for (Entity target : level.getEntities(player, search)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.distanceToSqr(centre) > RADIUS * RADIUS) continue;

            living.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, DURATION, SLOWNESS_LEVEL, false, true, true));
            living.addEffect(new MobEffectInstance(
                    ModEffects.DISORIENTATION, DURATION, 0, false, true, true));

            // Everyone caught feels it, whether or not the effects took hold.
            if (living instanceof ServerPlayer victim) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        victim, new EarthquakePacket(SHAKE_DURATION));
            }
        }

        // The caster: shaken, but not slowed or turned around. getEntities excluded
        // them from the sweep above, which is exactly the split this ability wants.
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new EarthquakePacket(SHAKE_DURATION));

        rumble(level, player, centre);
    }

    /**
     * The ground itself, thrown up in a ring around the bender.
     *
     * Drawn from the block actually underfoot at each point rather than from one
     * sample, so an earthquake across a shoreline throws up sand on one side and stone
     * on the other.
     */
    private static void rumble(ServerLevel level, ServerPlayer player, Vec3 centre) {
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 2.0F, 0.3F);

        for (int i = 0; i < 40; i++) {
            double angle = (Math.PI * 2.0 * i) / 40.0;
            double distance = 3.0 + (i % 5) * 3.5;

            double x = centre.x + Math.cos(angle) * distance;
            double z = centre.z + Math.sin(angle) * distance;

            BlockPos ground = EarthWorks.surfaceUnder(
                    level, BlockPos.containing(x, centre.y, z), 3, 4);
            if (ground == null) continue;

            BlockState under = level.getBlockState(ground.below());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under),
                    x, ground.getY() + 0.2, z, 6, 0.4, 0.1, 0.4, 0.08);
        }
    }
}
