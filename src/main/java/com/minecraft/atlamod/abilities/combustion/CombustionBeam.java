package com.minecraft.atlamod.abilities.combustion;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Left / Combustion. A TOGGLE: two seconds to bring the beam up, and then it holds
 * until it is switched off, boring through whatever the bender looks at and burning
 * anything standing in the line.
 *
 * Pressing the key again puts it down. That goes through isActive/deactivate, which
 * the dispatcher checks at the very top of startCharge — before the cooldown, before
 * the chi, and crucially before the WIND-UP. Switching a beam off must not make the
 * bender serve another two seconds, and it must not risk a misfire either: a cancel
 * that reached onChargeCancel would blow them up for turning their own ability off.
 *
 * The beam itself lives in {@link CombustionBeams}.
 */
public class CombustionBeam implements ChargedAbility {

    @Override
    public String getName() {
        return "Combustion Beam";
    }

    /** Chi drained per second while it is up. */
    public static final int CHI_PER_SECOND = 15;

    /** XP paid per second while it is up. */
    public static final int XP_PER_SECOND = 1;

    /**
     * Nothing up front: the beam is billed by the SECOND from the player tick, the same
     * way Sound wall and Metal shield are, and switches itself off when the chi runs
     * out. A toggle that cost a lump sum and then ran forever would have no limit at
     * all beyond the bender remembering to stop.
     */
    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0; // Paid by the second instead.
    }

    @Override
    public int getCooldownTicks() {
        return 100; // 5 seconds
    }

    @Override
    public int getChargeTicks() {
        return Combustion.MINIMUM_CHARGE_TICKS; // 2 seconds
    }

    /** Up already: the next press takes it down, with no wind-up and no misfire. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return CombustionBeams.has(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        CombustionBeams.stop(player);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Combustion.gather((ServerLevel) player.level(), player, ticksHeld, getChargeTicks());
    }

    /**
     * Letting go before the beam is up MISFIRES, like every other combustion ability.
     *
     * Only reachable while the beam is still being raised — once it is up, a press
     * goes through deactivate instead and never comes here.
     */
    @Override
    public void onChargeCancel(ServerPlayer player, BendingData data) {
        Combustion.misfire(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        CombustionBeams.start(player);
    }
}
