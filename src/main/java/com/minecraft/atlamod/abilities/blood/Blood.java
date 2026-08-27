package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.abilities.AbilitySupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bloodbending's shared parts.
 *
 * The mod's SIXTH sub-element, and the only one with a rule about who it may be used
 * ON. Blood strength gives every bloodbender a level of their own, and that level
 * decides the pecking order: you cannot bend somebody stronger than you, and somebody
 * weaker cannot bend you.
 *
 * That rule is enforced here rather than in each ability, so it cannot be forgotten by
 * the next one added — every bloodbending ability that picks a target asks
 * {@link #canBend} first.
 */
public final class Blood {

    /** Blood xp needed for a blood level. The same 200 the ordinary track uses. */
    public static final int XP_PER_LEVEL = AbilitySupport.XP_PER_LEVEL;

    private Blood() {
    }

    // ------------------------------------------------------------ the level

    /**
     * Adds blood xp and rolls it over into a blood level.
     *
     * A separate call from AbilitySupport.grantXp on purpose: bloodbending abilities
     * pay into THIS track and not the ordinary one, which is what makes a blood level
     * mean "how much bloodbending you have done" rather than "how much bending".
     */
    public static void grantXp(BendingData data, int amount) {
        if (amount <= 0) return;

        data.setBloodXp(data.getBloodXp() + amount);

        while (data.getBloodXp() >= XP_PER_LEVEL) {
            data.setBloodXp(data.getBloodXp() - XP_PER_LEVEL);
            data.setBloodLevel(data.getBloodLevel() + 1);
        }
    }

    /**
     * The same, and then tells the client.
     *
     * The blood track has its own sync packet because SyncBendingDataPacket is already
     * at the six fields StreamCodec.composite takes — and without the packet the HUD's
     * blood level would sit at zero however much bloodbending was done.
     */
    public static void grantXp(ServerPlayer player, BendingData data, int amount) {
        grantXp(data, amount);
        sync(player, data);
    }

    /** Pushes the blood track to the player's client. */
    public static void sync(ServerPlayer player, BendingData data) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.minecraft.atlamod.network.SyncBloodPacket(
                        data.getBloodXp(), data.getBloodLevel()));
    }

    /**
     * Whether {@code caster} is allowed to bend {@code target}.
     *
     * The rule, as the design states it: a lower-level bloodbender cannot touch a
     * higher-level one. Only players have blood levels, so mobs are always fair game.
     *
     * The protection is tied to the TARGET having Blood strength equipped. The level
     * itself accumulates whether or not the passive is in a slot, but it only protects
     * somebody who is actually carrying it — a passive that worked from the inventory
     * would not be a passive.
     */
    public static boolean canBend(ServerPlayer caster, LivingEntity target) {
        if (!(target instanceof ServerPlayer victim)) return true;

        BendingData victimData = victim.getData(ModAttachments.BENDING_DATA);
        if (!victimData.hasPassiveEquipped(BloodStrength.KEY)) return true;

        BendingData casterData = caster.getData(ModAttachments.BENDING_DATA);
        return casterData.getBloodLevel() >= victimData.getBloodLevel();
    }

    /**
     * The same check, with a message when it fails.
     *
     * Told rather than silently refused, because "nothing happened" is indistinguishable
     * from a broken ability — and the reason it failed is something the caster can
     * actually do something about.
     */
    public static boolean canBendOrTell(ServerPlayer caster, LivingEntity target) {
        if (canBend(caster, target)) return true;

        caster.displayClientMessage(Component.literal(
                "Their blood is stronger than yours.").withStyle(ChatFormatting.DARK_RED), true);
        return false;
    }

    // ------------------------------------------------------------- the look

    /** The red mist bloodbending works through. */
    public static void mist(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z,
                count, spread, spread, spread, 0.02);
    }

    /** Heavier, for something being wrenched about. */
    public static void wrench(ServerLevel level, LivingEntity target, int count) {
        if (!(target.level() instanceof ServerLevel targetLevel)) return;

        Vec3 middle = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        targetLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                middle.x, middle.y, middle.z, count, 0.3, 0.5, 0.3, 0.05);
    }

    /** The wet, unpleasant sound of the element. */
    public static void squelch(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * A bloodbending ability's damage.
     *
     * A pass-through for now, so every ability already routes through one place if a
     * bonus is ever added — Blood strength deliberately does NOT give one, since what
     * it grants is the pecking order rather than raw power.
     */
    public static float damage(BendingData data, float base) {
        return base;
    }
}
