package com.minecraft.atlamod.abilities.nobending;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Marks somebody's chi points, then makes you go and hit them.
 *
 * THE CAST IS NOT THE ABILITY. It deals nothing and applies nothing — it lights the
 * target up for ten seconds, and the block only lands once {@link ChiBlocks#PUNCHES_NEEDED}
 * hits have gone in inside that window. Everything about how that works lives in
 * {@link ChiBlocks}, because the mark outlives the cast and the punch counting has to be
 * reachable from the damage handler.
 *
 * NO CHI, and not as a discount: a non-bender has none. Both abilities on this path cost
 * nothing, which is what lets them go through the ordinary dispatcher rather than needing
 * a special case in it — {@code performCast} checks the pool against a cost of zero and
 * is satisfied. The real price is the cooldown and the work of landing five hits.
 *
 * NO XP EITHER, for the same reason meditating is refused: this path earns by killing.
 * See {@link NoBending#xpForKill}.
 */
public class ChiBlock implements Ability {

    /** The registry and cooldown key. Held as a constant because {@link ChiBlocks} stamps it. */
    public static final String KEY = "chi block";

    /**
     * Forty seconds, and it is stamped TWICE.
     *
     * The dispatcher stamps it on the cast, as it does for everything. {@link ChiBlocks}
     * then stamps it AGAIN when the attempt resolves — which for a wasted one is ten
     * seconds later. Without that second stamp a failed attempt would only cost thirty
     * seconds of waiting, since a third of the cooldown ran down while the mark was still
     * live and failing. Re-stamping is also what stops a successful block being chained:
     * fifteen seconds of shut-off chi behind a forty second wait is a window, where
     * back-to-back blocks would be a lock.
     */
    public static final int COOLDOWN_TICKS = 800;

    /**
     * How far away a target can be marked.
     *
     * INVENTED — the design gives no reach. Six blocks is deliberately short: this is a
     * path with no projectiles at all, and a mark that could be placed across a field
     * would be the ranged opener the path is not supposed to have. It is about as far as
     * you can point at somebody and mean it.
     */
    private static final double REACH = 6.0;

    /** The usual forgiveness, so a moving target does not need pixel-perfect aim. */
    private static final double TOLERANCE = 2.0;

    @Override
    public String getName() {
        return "Chi block";
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return COOLDOWN_TICKS;
    }

    /**
     * Refuses when there is nobody to mark, or when a mark is already running.
     *
     * Both refusals are FREE — canStart is checked before anything is spent or stamped —
     * which matters more here than for most: forty seconds is a long time to lose to a
     * mistimed press, and re-marking while a mark was already live would throw away the
     * punches already landed on the first one.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (ChiBlocks.hasMark(player)) {
            player.displayClientMessage(Component.literal(
                    "§cYou are already tracking someone."), true);
            return false;
        }

        if (Aiming.nearestAlongLook(player, REACH, TOLERANCE) == null) {
            player.displayClientMessage(Component.literal(
                    "§cNobody within reach."), true);
            return false;
        }
        return true;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Asked again rather than remembered from canStart: the two run a moment apart and
        // the target may have moved out of reach in between, which would otherwise mark
        // whoever canStart happened to see rather than whoever is actually there now.
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return;

        ChiBlocks.mark(level, player, target);
    }
}
