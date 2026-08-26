package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerPlayer;

/**
 * Masterclass / Air. An Air spout grown up: twice as tall, twice as strong, and
 * driven around the battlefield on the bender's crosshair instead of being set down
 * and left.
 *
 * A TOGGLE — press to raise it, press again to put it down, whether or not its thirty
 * seconds have run out. That second press is NOT a cast: it goes through
 * {@link Ability#isActive}, which the dispatcher checks before the cooldown gate and
 * before spending anything. Without that, a thirty second tornado behind a ten second
 * cooldown would be uncancellable for the first third of its life, and stopping it
 * would cost another 250 chi.
 *
 * The column itself lives in {@link AirSpouts} alongside the spouts, which it shares
 * everything with except its numbers and the steering.
 */
public class Tornado implements Ability {

    @Override
    public String getName() {
        return "Tornado";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 250;
    }

    @Override
    public int getXpReward() {
        return 25;
    }

    /**
     * Ten seconds, and it starts when the tornado is RAISED rather than when it comes
     * down. That is the ordinary behaviour of the dispatcher and the right one here:
     * the cooldown is on summoning another, and by the time a tornado has run its
     * thirty seconds the cooldown is long gone.
     */
    @Override
    public int getCooldownTicks() {
        return 200;
    }

    /** Up already: the next press takes it down. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return AirSpouts.hasTornado(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        AirSpouts.cancelTornado(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        AirSpouts.summonTornado(player);
    }
}
