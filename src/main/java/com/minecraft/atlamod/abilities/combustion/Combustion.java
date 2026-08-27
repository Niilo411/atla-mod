package com.minecraft.atlamod.abilities.combustion;

import com.minecraft.atlamod.BendingData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Combustionbending's shared parts.
 *
 * The mod's FIFTH sub-element, and the most dangerous to its own bender. Two things
 * are true of every ability in it and are enforced here rather than remembered per
 * ability:
 *
 *  - **A minimum two second wind-up.** Nothing in the element goes off on the press.
 *  - **Letting go early is a MISFIRE.** Every other charged ability in the mod treats
 *    an abandoned charge as free — the chi is only checked at the start and taken when
 *    the cast lands. Combustion does not: the power is already gathered by then, and
 *    it goes off where it stands. One primed TNT, on the bender.
 *
 * That second rule is the whole character of the element. Every other charge in the
 * mod can be thought better of; these cannot, so raising one is a commitment made
 * before the key goes down rather than after.
 */
public final class Combustion {

    /**
     * The wind-up every combustionbending ability serves, in ticks.
     *
     * Two seconds, and a MINIMUM rather than a fixed figure — Combustion nuke takes
     * ten and keeps them.
     *
     * The one exception is Combustion resistance, and it is barely one: a passive is
     * never cast, so there is no moment of use to put a wind-up in front of.
     */
    public static final int MINIMUM_CHARGE_TICKS = 40;

    private Combustion() {
    }

    /**
     * What happens when a combustion bender lets go too early.
     *
     * A single primed TNT, dropped exactly where they stand with no fuse to speak of.
     * It is a real PrimedTnt rather than a bare explosion so that it behaves like one
     * in every respect — it can be run from in the moment it has, it hurts whatever
     * else is nearby, and it looks like exactly what it is.
     *
     * Deliberately NOT survivable by standing still. The point is that a cancelled
     * combustion charge costs something real, and a misfire nobody had to react to
     * would not.
     */
    public static void misfire(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        PrimedTnt tnt = new PrimedTnt(level,
                player.getX(), player.getY(), player.getZ(), player);

        // A short fuse rather than vanilla's eighty ticks: this is a charge going off
        // in the bender's hands, not something they placed and walked away from.
        tnt.setFuse(10);
        level.addFreshEntity(tnt);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "The charge goes off in your hands!")
                .withStyle(net.minecraft.ChatFormatting.RED));

        boom(level, player.position(), 0.8F, 1.6F);
    }

    /**
     * The gather: heat drawn into a point, tightening as the wind-up fills.
     *
     * Shared by every combustion ability so the whole element reads the same way while
     * charging — and so a bender can see that something is being built, which matters
     * far more here than elsewhere given what letting go costs.
     */
    public static void gather(ServerLevel level, ServerPlayer at, int ticksHeld, int totalTicks) {
        double progress = Math.min(1.0, ticksHeld / (double) Math.max(1, totalTicks));

        Vec3 brow = at.getEyePosition().add(at.getLookAngle().scale(0.6));

        level.sendParticles(ParticleTypes.SMOKE, brow.x, brow.y, brow.z,
                2 + (int) (progress * 6), 0.4 - (progress * 0.3),
                0.4 - (progress * 0.3), 0.4 - (progress * 0.3), 0.01);

        // The white core the element is known for, only once it is nearly ready.
        if (progress > 0.6) {
            level.sendParticles(ParticleTypes.END_ROD, brow.x, brow.y, brow.z,
                    1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /**
     * The white stripe a combustion shot leaves behind it.
     *
     * The element's signature, and the reason it needs its own projectile style: every
     * other shot in the mod is drawn as a puff at its current position, where this one
     * has to read as a line already drawn through the air.
     */
    public static void stripe(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
    }

    /**
     * Sets off an explosion of the given power, behaving exactly as TNT does.
     *
     * TNT interaction rather than a mob one, so it breaks blocks the way a player
     * would expect a charge of this size to — combustionbending is a demolition
     * element, and an explosion that left the world untouched would be a strange thing
     * to call a nuke.
     */
    public static void detonate(ServerLevel level, ServerPlayer owner, Vec3 at, float power) {
        level.explode(owner, at.x, at.y, at.z, power, Level.ExplosionInteraction.TNT);
    }

    public static void boom(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * A combustion ability's damage.
     *
     * Combustion has no equivalent of Lightning Strength or Sound boosting, so this is
     * a pass-through. It exists so every combustion ability already routes through one
     * place if a bonus is ever added.
     */
    public static float damage(BendingData data, float base) {
        return base;
    }
}
