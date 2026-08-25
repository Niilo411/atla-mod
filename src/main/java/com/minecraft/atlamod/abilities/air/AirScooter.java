package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.SurfPlatformBlock;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Balanced / Air. A ball of churning air under the bender's feet, carrying them along
 * half a block off the ground and rather faster than they could run.
 *
 * The whole thing is built out of mechanics the CLIENT simulates, which is the point.
 * Holding a player at a height by setting their position server-side is the rubber
 * band that made Fire Rocket's old height cap feel awful — the client moves the
 * player, the server disagrees, and they fight about it twenty times a second. So
 * nothing here corrects the player at all:
 *
 *   - a REAL invisible block half a block above the ground carries them (the same
 *     trick Water Surf uses, at a different height),
 *   - a step height bonus lets them glide up over a rise instead of stopping dead,
 *   - Slow Falling gives the gentle drop when the ground falls away,
 *   - and Speed II makes the ride quicker than running.
 *
 * All four are ordinary vanilla mechanics the client already knows how to run, so the
 * ride is smooth and the server never has to argue with it.
 */
public class AirScooter implements ChanneledAbility {

    /** How far around the bender footing is laid, in blocks. */
    private static final int FOOTING_RADIUS = 2;

    /** How far above the feet to look for ground — enough to catch a one-block rise. */
    private static final int SCAN_UP = 2;

    /**
     * How far below the feet to look for ground.
     *
     * Deliberately short. Ride off a cliff and there is no footing to be had, so the
     * bender drifts down under Slow Falling until the ground is back within reach —
     * which is exactly the slow descent the ability is supposed to have. A deep scan
     * would instead build a floor in mid-air over the drop.
     */
    private static final int SCAN_DOWN = 4;

    /**
     * Added to the bender's step height while riding. Vanilla's 0.6 cannot manage the
     * full block a rise in the ground amounts to; 1.2 can, and vanilla's own step-up
     * is a smooth glide rather than a jump, so the climb looks like the scooter
     * riding over the lip.
     */
    private static final double STEP_BONUS_AMOUNT = 0.6;

    private static final ResourceLocation STEP_BONUS_ID =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "air_scooter_step");

    /**
     * Transient, so it is NEVER written to the player's NBT. A permanent modifier
     * would need the same login-and-respawn safety net Fire Rocket's flight flags do;
     * this one simply cannot outlive the session, whatever happens to the channel.
     */
    private static final AttributeModifier STEP_BONUS = new AttributeModifier(
            STEP_BONUS_ID, STEP_BONUS_AMOUNT, AttributeModifier.Operation.ADD_VALUE);

    /**
     * Speed II. Sprinting on the scooter works out around 7.9 blocks a second against
     * roughly 7.1 for sprint-jumping, so it is a little quicker than the fastest way
     * to travel on foot — which is what the ability is for.
     */
    private static final int SPEED_LEVEL = 1;

    /** Short, and topped up: an effect left long would outlast the ride. */
    private static final int EFFECT_DURATION = 40;
    private static final int EFFECT_REFRESH_BELOW = 20;

    @Override
    public String getName() {
        return "Air scooter";
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
    public int getChiPerSecond(BendingData data) {
        return 5;
    }

    /** Half an XP a second — one every two seconds, spread by the dispatcher. */
    @Override
    public double getXpPerSecond() {
        return 0.5;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && !step.hasModifier(STEP_BONUS_ID)) {
            step.addTransientModifier(STEP_BONUS);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.9F, 1.4F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        layFooting(player, level);
        keepEffect(player, MobEffects.MOVEMENT_SPEED, SPEED_LEVEL);
        keepEffect(player, MobEffects.SLOW_FALLING, 0);

        drawBubble(player, level);
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            step.removeModifier(STEP_BONUS_ID);
        }

        // The effects are left to lapse on their own. They are only 2 seconds long and
        // are not topped up once the ride ends, so they see themselves out — and
        // stripping Slow Falling the instant the key is released would drop a bender
        // who was still coming down.

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.6F, 1.3F);
    }

    /**
     * Lays the invisible footing the scooter actually rides on.
     *
     * One column at a time: find the surface, put half a block of platform in the air
     * directly above it, and the bender ends up standing exactly half a block off the
     * ground. Because it is a real block, the client carries them along on it without
     * the server having to hold them anywhere.
     */
    private static void layFooting(ServerPlayer player, ServerLevel level) {
        BlockState platform = Atlamod.SURF_PLATFORM.get().defaultBlockState()
                .setValue(SurfPlatformBlock.HEIGHT, SurfPlatformBlock.SCOOTER_HEIGHT);

        BlockPos feet = player.blockPosition();

        for (int dx = -FOOTING_RADIUS; dx <= FOOTING_RADIUS; dx++) {
            for (int dz = -FOOTING_RADIUS; dz <= FOOTING_RADIUS; dz++) {
                layColumn(level, platform, feet.getX() + dx, feet.getY(), feet.getZ() + dz);
            }
        }
    }

    /** Footing for one column, on top of the highest ground within reach of it. */
    private static void layColumn(ServerLevel level, BlockState platform, int x, int feetY, int z) {
        for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
            BlockPos ground = new BlockPos(x, feetY + dy, z);
            BlockState state = level.getBlockState(ground);

            // Our own footing is not ground — treating it as such would let each tick
            // build on the last and walk the bender steadily up into the sky.
            if (state.is(Atlamod.SURF_PLATFORM.get())) continue;

            // blocksMotion rather than isSolid, so the scooter also rides over stairs,
            // slabs and fences instead of ignoring them and looking for stone below.
            if (!state.blocksMotion()) continue;

            BlockPos above = ground.above();
            BlockState existing = level.getBlockState(above);
            if (existing.isAir() || existing.is(Atlamod.SURF_PLATFORM.get())) {
                level.setBlockAndUpdate(above, platform);
            }
            return; // only the topmost ground in this column
        }
    }

    /**
     * Tops an effect up only once it has nearly run out, rather than every tick.
     * Re-adding replaces the instance and resets its counter, so a constant refresh is
     * both wasteful on the wire and a good way to break effects that tick internally.
     */
    private static void keepEffect(ServerPlayer player,
                                   net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                   int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getDuration() > EFFECT_REFRESH_BELOW
                && current.getAmplifier() >= amplifier) {
            return;
        }

        player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, amplifier, false, false, true));
    }

    /** The ball of air being ridden, churning under the bender's feet. */
    private static void drawBubble(ServerPlayer player, ServerLevel level) {
        double cx = player.getX();
        double cy = player.getY();
        double cz = player.getZ();

        // A ring spinning under the feet, quickly enough to read as a ball being spun
        // rather than a puff sitting there.
        double spin = (player.tickCount % 8) / 8.0 * Math.PI * 2.0;
        for (int i = 0; i < 5; i++) {
            double angle = spin + (Math.PI * 2.0 * i / 5);
            double px = cx + Math.cos(angle) * 0.45;
            double pz = cz + Math.sin(angle) * 0.45;

            level.sendParticles(ParticleTypes.CLOUD, px, cy - 0.25, pz, 1, 0.05, 0.05, 0.05, 0.01);
        }

        level.sendParticles(ParticleTypes.CLOUD, cx, cy - 0.35, cz, 3, 0.2, 0.05, 0.2, 0.02);
    }
}
