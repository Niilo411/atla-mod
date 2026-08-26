package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.AbilityHandler;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityRegistry;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lightning. The bender stands ready with the key held, and any lightning
 * thrown AT them is caught rather than suffered — then held, and sent wherever they
 * left click.
 *
 * Two shapes in one class, and the split between them is the two phases:
 *
 *  - Phase 1 is the CHANNEL. Holding the key costs 50 chi a second and pays 5 xp a
 *    second, which is expensive to stand around in — it is a read of what the other
 *    bender is about to do, not something to leave running.
 *  - Phase 2 is the armed TWO-PHASE slot, entered the moment a bolt is caught.
 *    {@link #absorb} ends the channel as it arms, so the billing and the xp both
 *    stop there, exactly as the design asks. The caught bolt then waits
 *    indefinitely for a left click.
 *
 * Only lightning PROJECTILES can be caught — Lightning bolt's throw. The aura and
 * the ball are fields of current rather than something thrown at anyone, and there
 * is nothing in flight to take hold of.
 */
public class LightningRedirection implements ChanneledAbility, TwoPhaseAbility {

    /** Registry key. Also what sits in the channel and two-phase slots. */
    public static final String KEY = "lightning redirection";

    /** How the redirected bolt flies. Same shape as the one that was thrown at you. */
    private static final double SPEED = 3.0;
    private static final int LIFETIME = 40;
    private static final double HIT_RADIUS = 0.9;
    private static final double KNOCKBACK = 0.4;

    @Override
    public String getName() {
        return "Lightning redirection";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0; // Channels pay by the second.
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 50;
    }

    @Override
    public double getXpPerSecond() {
        return 5.0;
    }

    // -------------------------------------------------------------- phase 1

    /**
     * The element's one-second wind-up — and here it is a real cost, not decoration:
     * the catch is not up until it elapses, so redirection has to be raised BEFORE
     * the bolt is thrown rather than in reaction to seeing one coming.
     */
    @Override
    public int getWindupTicks() {
        return Lightning.MINIMUM_CHARGE_TICKS;
    }

    @Override
    public void onWindupTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Lightning.gather((ServerLevel) player.level(), player, ticksHeld, getWindupTicks());
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        Lightning.crack((ServerLevel) player.level(), player.position(), 0.4F, 1.9F);
    }

    /**
     * The ready stance: current gathered around the hands, so both the bender and
     * anyone aiming at them can see that the catch is up.
     */
    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 hands = player.getEyePosition()
                .add(player.getLookAngle().scale(0.8))
                .subtract(0.0, 0.3, 0.0);

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                hands.x, hands.y, hands.z, 3, 0.3, 0.3, 0.3, 0.01);
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Nothing to put away. If this stop is the CATCH rather than the key coming
        // up, absorb() has already armed phase 2 — and it deliberately arms after
        // ending the channel, so nothing here can clear it.
    }

    /**
     * Catches a lightning shot on its way into a player.
     *
     * Called from {@link BendingProjectiles} as the shot reaches its victim, which is
     * the only place that knows a bolt was about to land on somebody. Returns true
     * when the bolt was taken, and the shot is then spent without damaging anyone.
     *
     * @param damage what the bolt was going to hit for, carried through so the throw
     *               back is as strong as the throw that came in
     */
    public static boolean absorb(ServerPlayer victim, float damage) {
        BendingData data = victim.getData(ModAttachments.BENDING_DATA);

        if (!KEY.equals(data.getActiveChanneledAbility())) return false;

        Ability registered = AbilityRegistry.get(KEY);
        if (!(registered instanceof TwoPhaseAbility self)) return false;

        // The catch is not up until the wind-up has elapsed. Without this the ability
        // could be raised in the split second a bolt was already in flight, which is
        // exactly the reaction the one-second wind-up exists to rule out.
        if (registered instanceof ChanneledAbility channeled && !channeled.isReady(data)) {
            return false;
        }

        // Ending the channel first is what stops the chi and the xp at phase 2, and
        // it has to happen BEFORE arming: stopChannel syncs, and arming after it
        // means nothing in the stop path can clear the slot we just filled.
        AbilityHandler.endChannel(victim, data);

        data.setCaughtLightning(damage);
        AbilityHandler.armTwoPhase(victim, data, self);

        if (victim.level() instanceof ServerLevel level) {
            Lightning.spark(level, victim.getEyePosition(), 30, 0.4);
            Lightning.crack(level, victim.position(), 0.9F, 1.8F);
        }

        victim.sendSystemMessage(Component.literal("Lightning caught — left click to send it back.")
                .withStyle(ChatFormatting.AQUA));

        return true;
    }

    // -------------------------------------------------------------- phase 2

    /** Held indefinitely: the bender chooses when and where it goes. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    /** The caught bolt, coiled in the hands and waiting. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 held = player.getEyePosition()
                .add(player.getLookAngle().scale(1.0))
                .subtract(0.0, 0.2, 0.0);

        double phase = player.tickCount * 0.5;
        for (int i = 0; i < 3; i++) {
            double a = phase + (i * Math.PI * 2.0 / 3.0);
            Vec3 at = held.add(Math.cos(a) * 0.35, Math.sin(a * 1.4) * 0.2, Math.sin(a) * 0.35);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, held.x, held.y, held.z, 1, 0.08, 0.08, 0.08, 0.0);
    }

    /** Sends it back down the crosshair. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(1.0));

        // As hard as it was going to hit the catcher. Falls back to the damage of an
        // ordinary bolt if the figure was somehow lost — a relog mid-hold clears the
        // transient, and a redirect that fired for nothing would be worse.
        float damage = data.getCaughtLightning() > 0.0F ? data.getCaughtLightning() : 20.0F;
        data.setCaughtLightning(0.0F);

        BendingProjectiles.launch(player, from, player.getLookAngle(),
                new BendingProjectiles.Spec(SPEED, LIFETIME, Lightning.damage(data, damage),
                        HIT_RADIUS, KNOCKBACK, BendingProjectiles.Style.LIGHTNING));

        Lightning.spark(level, from, 20, 0.3);
        Lightning.crack(level, player.position(), 1.0F, 1.6F);
    }

}
