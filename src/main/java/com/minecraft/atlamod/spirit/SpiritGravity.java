package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The Spirit World's tide of low gravity.
 *
 * Vanilla's own {@link Attributes#GRAVITY} rather than a movement hack, which is worth
 * saying because the hack is the obvious approach and is worse in every way: the
 * attribute is applied by the physics itself, is synced to the client so the player's
 * own copy of the game agrees about how they fall, and survives anything that would
 * fight a per-tick velocity nudge. It exists in 1.21.1 with a base of 0.08.
 *
 * THE CYCLE IS DERIVED FROM THE LEVEL'S GAME TIME, not counted down in a field. That is
 * what makes it dimension-wide for free — every player asks the same clock and gets the
 * same answer, with no state to keep in step, nothing to persist, and no drift between
 * someone who was already there and someone who just arrived.
 */
public final class SpiritGravity {

    /** How often the tide comes in: every ten minutes. */
    public static final int PERIOD_TICKS = 20 * 60 * 10;

    /** How long it stays: three minutes out of each ten. */
    public static final int LOW_TICKS = 20 * 60 * 3;

    /**
     * How much of normal gravity is taken away.
     *
     * A MULTIPLIER rather than a flat subtraction, so it stays proportional if anything
     * else ever modifies gravity. -0.6 leaves 40% of 0.08, which is a floaty, drifting
     * fall rather than the near-weightlessness of a much larger cut — at very low
     * gravity a player who jumps is in the air long enough to be a nuisance.
     */
    private static final double REDUCTION = -0.6;

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_low_gravity");

    private SpiritGravity() {
    }

    /** Whether the Spirit World is currently in its low-gravity stretch. */
    public static boolean isLowNow(ServerLevel spirit) {
        return spirit.getGameTime() % PERIOD_TICKS < LOW_TICKS;
    }

    /** How many ticks until the current stretch ends, for anything that wants to say so. */
    public static int ticksLeft(ServerLevel spirit) {
        long into = spirit.getGameTime() % PERIOD_TICKS;
        return (int) (into < LOW_TICKS ? LOW_TICKS - into : PERIOD_TICKS - into);
    }

    /**
     * Brings one player's gravity into line with where they are and what time it is.
     *
     * Called every tick for every player, and does nothing at all on the overwhelming
     * majority of them: the modifier is only added when it is missing and only removed
     * when it is present, so the common case is one map lookup. Doing it per player
     * rather than per dimension is what handles someone walking out of the Spirit World
     * mid-tide — they are simply no longer in it on the next tick and the modifier comes
     * straight back off.
     */
    public static void tick(Player player) {
        boolean shouldBeLow = player.level() instanceof ServerLevel level
                && SpiritWorld.isSpiritWorld(level)
                && isLowNow(level);

        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;

        boolean isLow = gravity.getModifier(MODIFIER_ID) != null;
        if (shouldBeLow == isLow) return;

        if (shouldBeLow) {
            gravity.addTransientModifier(new AttributeModifier(
                    MODIFIER_ID, REDUCTION, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            announce(player, "§bThe Spirit World lightens. You drift.");
        } else {
            gravity.removeModifier(MODIFIER_ID);
            announce(player, "§7The weight of the world returns.");
        }
    }

    /**
     * Whether this entity is actually under the low-gravity modifier right now.
     *
     * Asks for the MODIFIER rather than re-deriving the tide from the clock, which is the
     * honest test: it is true of exactly whoever the physics is actually treating as light,
     * and it cannot drift out of step with {@link #tick}. Anything outside the Spirit
     * World, and every mob — which this never touches — answers false.
     *
     * WHAT IT IS FOR: a fall while drifting costs NOTHING. The cancel itself lives in
     * {@code ServerEvents}' fall handler, beside Air jump's, and this is the question it
     * asks.
     *
     * Worth saying why that is a cancel rather than a reduced figure. Vanilla charges for
     * fall DISTANCE, not for impact speed, so a player who drifts gently down is otherwise
     * billed exactly as if they had plummeted. Cancelling also takes the landing thud and
     * the puff of dust with it, which a reduced number would leave behind — somebody who
     * floats down should not land like a sack.
     *
     * It does mean the drop between two islands is free for three minutes in every ten.
     * That is the intent: the tide is the window in which the Spirit World can be crossed
     * without fear of the gaps, and the other seven minutes are when it cannot.
     */
    public static boolean isDrifting(net.minecraft.world.entity.LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.getModifier(MODIFIER_ID) != null;
    }

    /**
     * TRANSIENT, deliberately.
     *
     * A permanent modifier is written into the player's save, so a crash or a logout
     * during the low stretch would leave them drifting forever — and in the Overworld,
     * where nothing would ever take it off again. A transient one is gone the moment the
     * player is reloaded, and {@link #tick} puts it back a tick later if it is still
     * owed.
     */
    private static void announce(Player player, String message) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(Component.literal(message), true);
        }
    }
}
