package io.github.niilo411.elementalpaths.abilities.gravity;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.abilities.Ability;
import net.minecraft.server.level.ServerPlayer;

/**
 * Defensive / Gravity. Lightens the channeler's own steps — Speed IV, for as long as
 * they can pay for it.
 *
 * A TOGGLE billed by the second, the shape Sound Wall and Metal Shield already use
 * for a held defence with a running cost: press to start, press again to stop,
 * billed from the player tick rather than the dispatcher's channel machinery (see
 * ServerEvents#chargeSoundToggle and GravitySpeedBoosts).
 */
public class GravitySpeedBoost implements Ability {

    /** Registry key, also what ServerEvents' toggle section asks after. */
    public static final String KEY = "speed boost";

    public static final int CHI_PER_SECOND = 10;
    public static final int XP_PER_SECOND = 1;

    @Override
    public String getName() {
        return "Speed Boost";
    }

    /** Nothing up front: billed by the second from the player tick. */
    @Override
    public int getChiCost(ChannelingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public boolean isActive(ServerPlayer player, ChannelingData data) {
        return GravitySpeedBoosts.has(player);
    }

    @Override
    public void deactivate(ServerPlayer player, ChannelingData data) {
        GravitySpeedBoosts.stop(player);
    }

    @Override
    public boolean canStart(ServerPlayer player, ChannelingData data) {
        return data.getCurrentChi() >= CHI_PER_SECOND;
    }

    @Override
    public void execute(ServerPlayer player, ChannelingData data) {
        GravitySpeedBoosts.start(player);
    }
}
