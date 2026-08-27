package com.minecraft.atlamod.abilities.combustion;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Combustion. Two seconds of gathering, then three charges thrown down the
 * crosshair one behind the other, each leaving a white stripe to where it lands.
 *
 * The element's opening ability and its cheapest. Three small blasts rather than one
 * big one, which makes it the answer to a group or to something moving — a miss with
 * one still leaves two on the way.
 *
 * Letting go of the key before the two seconds are up MISFIRES. See Combustion.
 */
public class CombustionBombardment implements ChargedAbility {

    /** How many charges go out per cast. */
    private static final int COUNT = 3;

    /**
     * How far apart they are launched, in blocks.
     *
     * Spaced along the line rather than fired on a timer: the design asks for three
     * "in a row", and starting them at different distances gives exactly that without
     * the ability having to keep a countdown of its own alive after the cast.
     */
    private static final double SPACING = 1.4;

    /** INVENTED: the design gives bombardment no blast size, only the count. */
    private static final float POWER = 2.0F;

    private static final BendingProjectiles.Spec CHARGE = new BendingProjectiles.Spec(
            2.4, 60, 0.0F, 0.8, 0.0, BendingProjectiles.Style.COMBUSTION);

    @Override
    public String getName() {
        return "Combustion bombardment";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 100; // 5 seconds
    }

    @Override
    public int getChargeTicks() {
        return Combustion.MINIMUM_CHARGE_TICKS; // 2 seconds
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Combustion.gather((ServerLevel) player.level(), player, ticksHeld, getChargeTicks());
    }

    /** Letting go early sets the gathered charge off where the bender stands. */
    @Override
    public void onChargeCancel(ServerPlayer player, BendingData data) {
        Combustion.misfire(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();

        for (int i = 0; i < COUNT; i++) {
            Vec3 from = eye.add(look.scale(1.0 - (i * SPACING)));

            BendingProjectiles.launch(player, from, look,
                    CHARGE.withImpact((hitLevel, at) ->
                            Combustion.detonate(hitLevel, player, at, POWER)));
        }

        Combustion.boom(level, player.position(), 1.0F, 1.4F);
    }
}
