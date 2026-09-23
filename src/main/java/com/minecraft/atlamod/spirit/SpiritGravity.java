package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.AtlaConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

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

    /**
     * How often the tide comes in, and how long it stays, both in ticks.
     *
     * SETTINGS now (AtlaConfig's spiritGravityTide section), authored there in real
     * MINUTES rather than ticks since nobody thinks in ticks — these two convert that
     * up front so the rest of the class stays exactly the tick arithmetic it always
     * was. 10 minutes / 3 minutes are the shipped defaults.
     */
    public static int periodTicks() {
        return AtlaConfig.spiritTidePeriodMinutes() * 20 * 60;
    }

    public static int lowTicks() {
        return AtlaConfig.spiritTideDurationMinutes() * 20 * 60;
    }

    /**
     * How much of normal gravity is left during the tide, as a MULTIPLIER — negative
     * because {@link AttributeModifier.Operation#ADD_MULTIPLIED_BASE} adds it to 1.0,
     * so this is "how much to take away" rather than "how much is left".
     *
     * Reads AtlaConfig's gravityPercent (0-100, 40 shipped) rather than a constant, so
     * it stays proportional whatever the setting: 40% leaves a floaty, drifting fall;
     * 0% is true weightlessness; 100% makes the tide purely cosmetic.
     */
    private static double reduction() {
        return (AtlaConfig.spiritTideGravityPercent() / 100.0) - 1.0;
    }

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_low_gravity");

    private SpiritGravity() {
    }

    /**
     * Whether the Spirit World is currently in its low-gravity stretch.
     *
     * A DISABLED tide (AtlaConfig's spiritGravityTide.enabled) never answers true —
     * the single choke point that switches the whole feature off, since {@link #tick}
     * and {@link #isDrifting} both work from this rather than re-checking the setting
     * themselves.
     */
    public static boolean isLowNow(ServerLevel spirit) {
        if (!AtlaConfig.spiritTideEnabled()) return false;

        return spirit.getGameTime() % periodTicks() < lowTicks();
    }

    /** How many ticks until the current stretch ends, for anything that wants to say so. */
    public static int ticksLeft(ServerLevel spirit) {
        int period = periodTicks();
        int low = lowTicks();
        long into = spirit.getGameTime() % period;
        return (int) (into < low ? low - into : period - into);
    }

    /**
     * Brings one living thing's gravity into line with where they are and what time
     * it is.
     *
     * Called every tick for every player, and does nothing at all on the overwhelming
     * majority of them: the modifier is only added when it is missing and only removed
     * when it is present, so the common case is one map lookup. Doing it per entity
     * rather than per dimension is what handles someone walking out of the Spirit World
     * mid-tide — they are simply no longer in it on the next tick and the modifier comes
     * straight back off.
     *
     * TAKES A LivingEntity, not a Player, so the same method serves mobs when
     * AtlaConfig's affectsMobs is on — see the mob half of this in ServerEvents'
     * onEntityTick. Nothing else about the logic differs; {@link #announce} is what
     * quietly does nothing for anything that isn't a player.
     */
    public static void tick(LivingEntity entity) {
        boolean shouldBeLow = entity.level() instanceof ServerLevel level
                && SpiritWorld.isSpiritWorld(level)
                && isLowNow(level);

        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;

        boolean isLow = gravity.getModifier(MODIFIER_ID) != null;
        if (shouldBeLow == isLow) return;

        if (shouldBeLow) {
            gravity.addTransientModifier(new AttributeModifier(
                    MODIFIER_ID, reduction(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            announce(entity, "§bThe Spirit World lightens. You drift.");
        } else {
            gravity.removeModifier(MODIFIER_ID);
            announce(entity, "§7The weight of the world returns.");
        }
    }

    /**
     * Whether this entity is actually under the low-gravity modifier right now.
     *
     * Asks for the MODIFIER rather than re-deriving the tide from the clock, which is the
     * honest test: it is true of exactly whoever the physics is actually treating as light,
     * and it cannot drift out of step with {@link #tick}. Anything outside the Spirit
     * World answers false, and so does every mob UNLESS AtlaConfig's affectsMobs is on —
     * see the class note.
     *
     * WHAT IT IS FOR: a fall while drifting costs NOTHING, when AtlaConfig's
     * noFallDamage is also on — see {@link #cancelsFallDamage}, which is what the fall
     * handler actually asks.
     *
     * Worth saying why that is a cancel rather than a reduced figure. Vanilla charges for
     * fall DISTANCE, not for impact speed, so a player who drifts gently down is otherwise
     * billed exactly as if they had plummeted. Cancelling also takes the landing thud and
     * the puff of dust with it, which a reduced number would leave behind — somebody who
     * floats down should not land like a sack.
     *
     * It does mean the drop between two islands is free while the tide is in, by
     * default. That is the intent: the tide is the window in which the Spirit World can
     * be crossed without fear of the gaps, and the rest of the cycle is when it cannot.
     */
    public static boolean isDrifting(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.getModifier(MODIFIER_ID) != null;
    }

    /**
     * Whether a fall should cost this entity nothing right now.
     *
     * SEPARATE FROM {@link #isDrifting} on purpose — the config lets the two be set
     * independently, so a lighter fall can still be made to hurt if that is what a
     * server wants, or gravity can stay ordinary while falls are forgiven anyway. This
     * is the one question {@code ServerEvents}' fall handler actually needs.
     */
    public static boolean cancelsFallDamage(LivingEntity entity) {
        return AtlaConfig.spiritTideNoFallDamage() && isDrifting(entity);
    }

    /**
     * TRANSIENT, deliberately.
     *
     * A permanent modifier is written into the entity's save, so a crash or a logout
     * during the low stretch would leave them drifting forever — and in the Overworld,
     * where nothing would ever take it off again. A transient one is gone the moment the
     * entity is reloaded, and {@link #tick} puts it back a tick later if it is still
     * owed.
     */
    private static void announce(LivingEntity entity, String message) {
        if (entity instanceof ServerPlayer server) {
            server.displayClientMessage(Component.literal(message), true);
        }
    }
}
