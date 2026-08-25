package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Balanced / Water. Rise to the surface and run across it.
 *
 * The footing is an invisible sliver laid in the AIR above the water rather than
 * anything done to the water itself, so what the bender sees is the surface they
 * were already swimming in. Freezing it instead does work — it is how vanilla's
 * Frost Walker solves the same problem — but it plainly looks like ice.
 *
 * Either way the point is that a real block carries the player, so the client walks
 * on it normally. Pinning the player's position to the waterline every tick would
 * have the server correcting the client constantly, which rubber-bands rather than
 * surfs — the same failure as Fire Rocket's old height cap.
 *
 * Each platform removes itself on a scheduled tick, so nothing here has to remember
 * to clean up behind the bender.
 */
public class WaterSurf implements ChanneledAbility {

    /** Key for the upgrade that quickens the surf. */
    public static final String SWIFT_CURRENT = "water_surf_swift";

    /** What the upgrade costs, in levels. */
    private static final int SWIFT_CURRENT_COST = 10;

    /** Speed I normally, Speed II once the upgrade is bought. */
    private static final int BASE_SPEED_LEVEL = 0;
    private static final int UPGRADED_SPEED_LEVEL = 1;

    /** How far either side of the bender footing is laid. */
    private static final int FOOTING_RADIUS = 2;


    @Override
    public String getName() {
        return "Water Surf";
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
        return 10;
    }

    @Override
    public double getXpPerSecond() {
        return 3;
    }

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(new AbilityUpgrade(
                SWIFT_CURRENT,
                "Swift Current",
                "Speed II instead of I while surfing",
                SWIFT_CURRENT_COST));
    }

    /**
     * Deliberately NOT gated on the canteen, for the same reason the other water
     * abilities that name their own source are not: this one has the bender starting
     * out swimming in it.
     */
    @Override
    public boolean requiresWater() {
        return false;
    }

    /** Has to be started from the water. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (player.isInWater()) return true;

        player.displayClientMessage(
                Component.literal("§bYou must be in water to surf!"), true);
        return false;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        liftToSurface(player);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        layFooting(player, level);
        applySpeed(player, data);

        // Spray thrown up either side, so it reads as riding the water rather than
        // standing on it.
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
            level.sendParticles(ParticleTypes.SPLASH,
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    6, 0.4, 0.05, 0.4, 0.05);
        }
    }

    /**
     * Lays footing under the bender so there is something to run on.
     *
     * The platform goes in the AIR block ABOVE the water rather than replacing it.
     * Freezing the surface works, but it plainly looks like ice; leaving the water
     * untouched and standing on an invisible sliver above it looks like running on
     * water, which is the point of the ability.
     *
     * Only full source blocks are built over, so the surf cannot lay a walkway across
     * a pond it happens to be passing above.
     */
    private static void layFooting(ServerPlayer player, ServerLevel level) {
        BlockState platform = Atlamod.SURF_PLATFORM.get().defaultBlockState();
        BlockPos feet = player.blockPosition();

        for (BlockPos water : BlockPos.betweenClosed(
                feet.offset(-FOOTING_RADIUS, -2, -FOOTING_RADIUS),
                feet.offset(FOOTING_RADIUS, 0, FOOTING_RADIUS))) {

            if (!level.getFluidState(water).is(FluidTags.WATER)) continue;
            if (!level.getFluidState(water).isSource()) continue;

            // Only where the water actually has a surface — a source with more water
            // on top of it is somewhere below the waterline, not on it.
            BlockPos above = water.above();
            if (level.getFluidState(above).is(FluidTags.WATER)) continue;

            BlockState existing = level.getBlockState(above);
            if (!existing.isAir() && !existing.is(Atlamod.SURF_PLATFORM.get())) continue;

            level.setBlockAndUpdate(above.immutable(), platform);
        }
    }

    /**
     * Tops the speed up only once it has nearly run out, rather than every tick.
     * Re-adding an effect replaces the instance and resets its counter, so a constant
     * refresh is both wasteful and a good way to interfere with effects that tick.
     */
    private static void applySpeed(ServerPlayer player, BendingData data) {
        int amplifier = data.hasUpgrade(SWIFT_CURRENT) ? UPGRADED_SPEED_LEVEL : BASE_SPEED_LEVEL;

        MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (current != null && current.getDuration() > 20 && current.getAmplifier() >= amplifier) {
            return;
        }

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, 60, amplifier, false, false, true));
    }

    /** Raises a swimming bender to stand on top of the water they were in. */
    private static void liftToSurface(ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();

        int surface = pos.getY();
        while (surface < level.getMaxBuildHeight()
                && level.getFluidState(new BlockPos(pos.getX(), surface, pos.getZ()))
                        .is(FluidTags.WATER)) {
            surface++;
        }

        player.teleportTo(player.getX(), surface, player.getZ());
        player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        player.hurtMarked = true;
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // The speed is left to run out rather than stripped: it may not be ours to
        // take away, and 60 ticks is short enough that it fades on its own.
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.6F, 0.9F);
    }
}
