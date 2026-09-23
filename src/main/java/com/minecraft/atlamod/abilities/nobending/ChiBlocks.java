package com.minecraft.atlamod.abilities.nobending;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.network.SyncStatsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chi block's two halves: who is being AIMED at, and who has been SHUT OFF.
 *
 * THE ABILITY IS NOT THE BLOW, IT IS PERMISSION TO LAND ONE. Casting it does no damage
 * and applies no effect — it marks somebody for ten seconds, and the caster then has to
 * actually reach them and land {@value #PUNCHES_NEEDED} hits inside that window. Miss the
 * window and the cast is spent for nothing. That is the whole character of the path: a
 * non-bender's answer to bending is not a better projectile, it is getting close enough.
 *
 * TRACKED HERE RATHER THAN ON THE ABILITY, because a mark outlives its own cast by ten
 * seconds and has to be ticked, and because the punch counter has to be reachable from
 * the damage handler — which knows nothing about abilities and should not have to.
 *
 * THE MARK IS VISIBLE TO EVERYONE, deliberately. Glowing is the only vanilla effect that
 * renders through walls, so the caster can keep track of a target that has broken line of
 * sight — and the target can see they have been marked, which is what makes running a real
 * option rather than a thing that happens to them off screen.
 */
public final class ChiBlocks {

    /** How long the mark lasts before the attempt is wasted. */
    public static final int MARK_TICKS = 200;

    /** Hits needed inside the mark. */
    public static final int PUNCHES_NEEDED = 5;

    /** How long a successful block holds. */
    public static final int BLOCK_TICKS = 300;

    /**
     * Slowness while blocked.
     *
     * Level II rather than I. The chi half of this does nothing at all to somebody who
     * was never a bender, and mobs have no chi to shut off either — so without a real
     * movement penalty the ability would land on half the things in the game and do
     * visibly nothing. The slow is the part that is always true.
     */
    private static final int SLOWNESS_AMPLIFIER = 1;

    private static final List<Mark> MARKS = new ArrayList<>();

    private ChiBlocks() {
    }

    private static final class Mark {
        final ServerLevel level;
        final UUID casterId;
        final LivingEntity victim;
        int ticksLeft = MARK_TICKS;
        int punches = 0;

        Mark(ServerLevel level, UUID casterId, LivingEntity victim) {
            this.level = level;
            this.casterId = casterId;
            this.victim = victim;
        }
    }

    // ==========================================================================
    //  Marking
    // ==========================================================================

    /** Marks a victim. A second cast on the same one replaces the first rather than stacking. */
    public static void mark(ServerLevel level, ServerPlayer caster, LivingEntity victim) {
        MARKS.removeIf(mark -> mark.victim == victim);
        MARKS.add(new Mark(level, caster.getUUID(), victim));

        victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false, true));

        caster.displayClientMessage(Component.literal(
                "§eChi points exposed — land " + PUNCHES_NEEDED + " hits in 10s."), true);
    }

    /** Whether this caster already has a mark running, so a second cast can be refused. */
    public static boolean hasMark(ServerPlayer caster) {
        for (Mark mark : MARKS) {
            if (mark.casterId.equals(caster.getUUID())) return true;
        }
        return false;
    }

    /**
     * A hit landed. Returns whether it counted, purely so the caller can skip the rest.
     *
     * ONLY THE PLAYER WHO CAST IT COUNTS, and only on the victim THEY marked. Somebody
     * else beating on the same target is not doing the chi blocking, and a caster hitting
     * a different mob is not either.
     */
    public static boolean countHit(LivingEntity attacker, LivingEntity victim) {
        // FIRST, and it matters: this is called for every melee hit landed anywhere in
        // the world, and the snapshot below allocates. Almost always there is nothing
        // being tracked at all.
        if (MARKS.isEmpty()) return false;

        if (!(attacker instanceof ServerPlayer caster)) return false;

        for (Mark mark : List.copyOf(MARKS)) {
            if (mark.victim != victim) continue;
            if (!mark.casterId.equals(caster.getUUID())) continue;

            mark.punches++;

            if (mark.punches >= PUNCHES_NEEDED) {
                MARKS.remove(mark);
                land(mark, caster);
            } else {
                caster.displayClientMessage(Component.literal(
                        "§e" + mark.punches + " / " + PUNCHES_NEEDED), true);
            }
            return true;
        }
        return false;
    }

    /**
     * The block itself.
     *
     * The mark's glow is cleared on the way, or a blocked victim would keep shining for
     * whatever was left of the ten seconds and read as still being hunted.
     */
    private static void land(Mark mark, ServerPlayer caster) {
        LivingEntity victim = mark.victim;

        victim.removeEffect(MobEffects.GLOWING);
        block(victim, BLOCK_TICKS);

        caster.displayClientMessage(Component.literal("§aChi blocked!"), true);

        mark.level.playSound(null, victim.blockPosition(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        mark.level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                30, 0.4, 0.5, 0.4, 0.2);
    }

    /**
     * Shuts somebody's chi off for a while.
     *
     * Public because chi blocking is a STATE rather than one ability's private business —
     * anything else that ought to cause it later should come through here rather than
     * setting the counter itself, so the slowness and the message cannot be forgotten.
     */
    public static void block(LivingEntity victim, int ticks) {
        victim.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, ticks, SLOWNESS_AMPLIFIER, false, true, true));

        if (!(victim instanceof ServerPlayer player)) return;

        BendingData data = player.getData(ModAttachments.BENDING_DATA);
        data.setChiBlockedTicks(ticks);
        player.setData(ModAttachments.BENDING_DATA, data);

        player.displayClientMessage(Component.literal(
                "§cYour chi is blocked!"), true);
    }

    // ==========================================================================
    //  Ticking
    // ==========================================================================

    /** Iterates a SNAPSHOT: this can end a mark, and a death handler can call back in here. */
    public static void tickAll(MinecraftServer server) {
        if (MARKS.isEmpty()) return;

        for (Mark mark : List.copyOf(MARKS)) {
            if (!MARKS.contains(mark)) continue;

            if (!advance(mark, server)) {
                MARKS.remove(mark);
            }
        }
    }

    private static boolean advance(Mark mark, MinecraftServer server) {
        if (!mark.victim.isAlive()) return false;

        ServerPlayer caster = server.getPlayerList().getPlayer(mark.casterId);
        if (caster == null) return false;

        if (mark.ticksLeft-- > 0) return true;

        // Out of time. The cast is spent and the cooldown is stamped afresh from HERE
        // rather than from the cast, so a wasted attempt really does cost the full wait
        // rather than the wait minus the ten seconds already spent watching it fail.
        mark.victim.removeEffect(MobEffects.GLOWING);

        BendingData data = caster.getData(ModAttachments.BENDING_DATA);
        data.setCooldown(ChiBlock.KEY, com.minecraft.atlamod.abilities.AbilityTuning
                .cooldownTicks(ChiBlock.KEY, ChiBlock.COOLDOWN_TICKS));
        caster.setData(ModAttachments.BENDING_DATA, data);
        PacketDistributor.sendToPlayer(caster, SyncStatsPacket.of(data));

        caster.displayClientMessage(Component.literal(
                "§cThey slipped away — chi block wasted."), true);

        return false;
    }

    /** Ticks a player's own blocked counter down. Called from the player tick. */
    public static void tickBlocked(ServerPlayer player, BendingData data) {
        if (!data.isChiBlocked()) return;

        data.setChiBlockedTicks(data.getChiBlockedTicks() - 1);

        if (!data.isChiBlocked()) {
            player.displayClientMessage(Component.literal("§aYour chi flows again."), true);
        }
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        MARKS.removeIf(mark -> mark.casterId.equals(player.getUUID()) || mark.victim == player);
    }

    public static void forgetLevel(ServerLevel level) {
        MARKS.removeIf(mark -> mark.level == level);
    }
}
