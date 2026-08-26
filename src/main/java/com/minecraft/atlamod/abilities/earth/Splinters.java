package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Earth. Six shards of stone torn out of the ground and thrown one at a
 * time, hard enough that four of them will finish anything a zombie's size.
 *
 * Air splinters' heavier twin, on the same charge-then-click shape: where those are
 * quick cuts that slow what they touch, these are simply hits. Faster, smaller, and
 * far more punishing — and correspondingly harder to land, since a shard this size
 * moving this quickly wants to be aimed properly.
 */
public class Splinters implements ChargedAbility, TwoPhaseAbility {

    /** Two seconds to tear them loose, matching Air splinters. */
    private static final int CHARGE_TICKS = 40;

    /** Thrown one at a time. */
    private static final int SHOTS = 6;

    /**
     * 2.5 hearts each, so four of them come to exactly the twenty a zombie has.
     *
     * That figure only holds because the shot pierces invulnerability frames — see
     * the spec below — and because indirectMagic bypasses armour, so a zombie's own
     * two points do not quietly stretch it to five hits.
     */
    private static final float DAMAGE = 5.0F;

    /** Faster than the air version, which is already the quickest thing the mod threw. */
    private static final double SPEED = 3.5;

    /** At this speed it still carries about 45 blocks. */
    private static final int LIFETIME = 13;

    /** Tight. "Needs good aim" is the ability, so the hitbox has to mean it. */
    private static final double HIT_RADIUS = 0.5;

    /** A shard nudges; it does not throw. */
    private static final double KNOCKBACK = 0.15;

    /**
     * One splinter.
     *
     * piercesInvulnerability is the load-bearing flag: vanilla ignores a second hit of
     * equal size within ten ticks of the first, so six shots landing in quick
     * succession would have five of them do nothing at all — and "four kills a zombie"
     * would be false for anyone who did not carefully pause between clicks.
     */
    private static final BendingProjectiles.Spec SHOT = new BendingProjectiles.Spec(
            SPEED, LIFETIME, DAMAGE, HIT_RADIUS, KNOCKBACK,
            BendingProjectiles.Style.STONE, null, true);

    @Override
    public String getName() {
        return "Splinters";
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
        return 200; // 10 seconds, starting from the last of the six
    }

    @Override
    public int getChargeTicks() {
        return CHARGE_TICKS;
    }

    /** Six clicks, not one. */
    @Override
    public int getShots() {
        return SHOTS;
    }

    /** Nothing else already held. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    /** Stone breaking loose around the bender's feet and gathering to them. */
    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = ticksHeld / (float) CHARGE_TICKS;
        double radius = 1.8 - (1.1 * power);
        double spin = (player.tickCount % 10) / 10.0 * Math.PI * 2.0;

        for (int i = 0; i < SHOTS; i++) {
            double angle = spin + (Math.PI * 2.0 * i / SHOTS);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    player.getX() + Math.cos(angle) * radius,
                    player.getY() + 0.9,
                    player.getZ() + Math.sin(angle) * radius,
                    1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** The charge only tears them loose; the clicks throw them. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    /**
     * The shards still in hand, turning around the bender.
     *
     * Drawn from the live count rather than from SHOTS, so throwing one visibly leaves
     * five — the ring is the ammunition counter.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        int remaining = data.getTwoPhaseShots();
        if (remaining <= 0) return;

        double spin = (player.tickCount % 20) / 20.0 * Math.PI * 2.0;
        for (int i = 0; i < remaining; i++) {
            double angle = spin + (Math.PI * 2.0 * i / remaining);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    player.getX() + Math.cos(angle) * 0.9,
                    player.getY() + 1.3,
                    player.getZ() + Math.sin(angle) * 0.9,
                    1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    /** One click, one shard. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.8));

        BendingProjectiles.launch(player, from, look, SHOT);

        // Pitch climbs as they run down, so the last shard is audibly the last.
        float pitch = 1.0F + (0.12F * (SHOTS - data.getTwoPhaseShots()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.1F, pitch);
    }
}
