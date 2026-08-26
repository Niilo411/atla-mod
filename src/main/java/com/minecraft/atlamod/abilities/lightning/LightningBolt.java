package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Right / Lightning. Five seconds of winding up produces a live string of current
 * held in the bender's hand, bending and whipping along wherever they look, until a
 * left click throws it exactly down the crosshair.
 *
 * BOTH held shapes at once, the way Fireball is: the charge BUILDS the string, and
 * what the completed charge produces is the armed two-phase slot that the click then
 * looses. The dispatcher deliberately skips the cooldown when arming a two-phase
 * ability, so the one second starts on the THROW rather than when the string is
 * finished — otherwise it would be long gone before the bender had aimed.
 *
 * The string is drawn in {@link #onArmedTick}, which exists precisely because what
 * is being held differs per ability and the tick loop cannot get it right for all
 * of them.
 */
public class LightningBolt implements ChargedAbility, TwoPhaseAbility {

    /** Key of the upgrade that calls down a real bolt wherever the shot ends. */
    public static final String STORM_CALLER = "lightning_bolt_storm_caller";

    /** 20 hp, as specced — ten hearts, enough to end most things in one throw. */
    private static final float DAMAGE = 20.0F;

    /**
     * How the thrown string flies. Fast and long-lived: it is a bolt, and a lightning
     * shot that arced gently would look wrong however well it hit.
     */
    private static final BendingProjectiles.Spec SHOT = new BendingProjectiles.Spec(
            3.0, 40, DAMAGE, 0.9, 0.4, BendingProjectiles.Style.LIGHTNING);

    /** How far out the held string reaches when the bender is looking at open sky. */
    private static final double STRING_REACH = 12.0;

    /** How many segments the string is drawn in. More is smoother and more packets. */
    private static final int STRING_SEGMENTS = 12;

    @Override
    public String getName() {
        return "Lightning bolt";
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
        return 20; // 1 second, and it starts on the throw. See the class note.
    }

    @Override
    public int getChargeTicks() {
        return 100; // 5 seconds
    }

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(new AbilityUpgrade(
                STORM_CALLER,
                "Storm Caller",
                "The bolt calls down real lightning wherever it lands",
                15));
    }

    // ------------------------------------------------------------- charging

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        Lightning.crack((ServerLevel) player.level(), player.position(), 0.4F, 1.8F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        // Gathers in the hand, tightening as it fills, so the wind-up is visibly
        // going somewhere across five seconds rather than just taking a while.
        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 hand = handOf(player);

        Lightning.spark(level, hand, 2 + (int) (progress * 6), 0.55 - (progress * 0.4));
    }

    @Override
    public void onChargeCancel(ServerPlayer player, BendingData data) {
        // Nothing to put away: the gather was only ever particles, and no chi was
        // spent — the dispatcher only checks it at charge start and takes it when
        // the cast lands, so letting go early is free.
    }

    // ---------------------------------------------------------------- armed

    /**
     * The finished string, redrawn every tick from the hand to wherever the bender
     * is looking.
     *
     * Bent rather than straight: each segment is pushed off the line by a sine that
     * moves with the tick count, so the string whips and coils along its length
     * instead of hanging there as a laser. That bending is the ability's whole look.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = handOf(player);
        Vec3 to = aimPoint(player);

        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 0.01) return;

        Vec3 direction = along.scale(1.0 / length);

        // Two axes across the line, so the string can wander in both rather than
        // waving flat like a skipping rope seen edge on.
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) side = direction.cross(new Vec3(1.0, 0.0, 0.0));
        side = side.normalize();
        Vec3 up = direction.cross(side).normalize();

        double phase = player.tickCount * 0.45;

        for (int i = 0; i <= STRING_SEGMENTS; i++) {
            double t = i / (double) STRING_SEGMENTS;

            // Pinned at both ends and loosest in the middle, so it reads as a string
            // held in the hand and aimed, not a ribbon flapping free.
            double slack = Math.sin(t * Math.PI) * 0.45;

            double wobbleSide = Math.sin(phase + t * 7.0) * slack;
            double wobbleUp = Math.cos(phase * 1.3 + t * 5.0) * slack;

            Vec3 at = from.add(direction.scale(length * t))
                    .add(side.scale(wobbleSide))
                    .add(up.scale(wobbleUp));

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Throws it straight down the crosshair.
     *
     * The aim is taken HERE rather than when the charge finished, like Fireball and
     * Air cannon: five seconds is a long time to hold a line on something moving,
     * and a shot with nothing to show for a miss should be aimed at the last moment.
     */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = handOf(player);
        Vec3 direction = player.getLookAngle();

        BendingProjectiles.Spec spec = new BendingProjectiles.Spec(
                SHOT.speed(), SHOT.lifetime(), Lightning.damage(data, DAMAGE),
                SHOT.hitRadius(), SHOT.knockback(), SHOT.style());

        // Storm Caller: a REAL bolt wherever the shot ends, on a target or on the
        // ground. Real, not visual-only, because calling down actual lightning — fire
        // and all — is what the upgrade is.
        if (data.hasUpgrade(STORM_CALLER)) {
            spec = spec.withImpact(Lightning::realStrike);
        }

        BendingProjectiles.launch(player, from, direction, spec);

        Lightning.spark(level, from, 20, 0.3);
        Lightning.crack(level, player.position(), 1.0F, 1.7F);
    }

    // --------------------------------------------------------------- shared

    /** Roughly where the string is held — just ahead of the bender, at chest height. */
    private static Vec3 handOf(ServerPlayer player) {
        return player.getEyePosition()
                .add(player.getLookAngle().scale(0.9))
                .subtract(0.0, 0.25, 0.0);
    }

    /**
     * Where the far end of the string sits: on whatever the bender is looking at, or
     * out at arm's length in open air.
     */
    private static Vec3 aimPoint(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        net.minecraft.world.phys.HitResult hit = player.level().clip(
                new net.minecraft.world.level.ClipContext(
                        eye, eye.add(look.scale(STRING_REACH)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));

        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? hit.getLocation()
                : eye.add(look.scale(STRING_REACH));
    }

    /**
     * The charge's payload is the ordinary cast, which the dispatcher runs through
     * performCast — and that ARMS the two-phase slot rather than firing anything.
     * The string appearing in hand is the whole of it.
     */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Lightning.crack((ServerLevel) player.level(), player.position(), 0.7F, 1.2F);
    }

}
