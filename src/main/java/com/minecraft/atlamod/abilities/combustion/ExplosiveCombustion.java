package com.minecraft.atlamod.abilities.combustion;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Combustion. Three seconds of gathering, then ONE charge that lands with the
 * force of four sticks of TNT.
 *
 * Bombardment's opposite trade: one blast instead of three, and each one worth far
 * more than a bombardment charge. It has to be aimed, and a miss is three seconds and
 * 150 chi gone.
 *
 * Letting go early MISFIRES, and at this size that is a real threat to the bender.
 */
public class ExplosiveCombustion implements ChargedAbility {

    /**
     * "The equivalent destruction to 4 tnt".
     *
     * One stick of TNT is power 4.0. Explosion size does NOT scale linearly with
     * power — the radius goes roughly as the power — so four sticks' worth of
     * destruction is nearer double the figure than four times it. 8.0 is the reading
     * taken here, and it is the one number in the ability that is interpretation
     * rather than specification.
     */
    private static final float POWER = 8.0F;

    private static final BendingProjectiles.Spec CHARGE = new BendingProjectiles.Spec(
            2.2, 80, 0.0F, 1.0, 0.0, BendingProjectiles.Style.COMBUSTION);

    @Override
    public String getName() {
        return "Explosive combustion";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    @Override
    public int getCooldownTicks() {
        return 100; // 5 seconds
    }

    @Override
    public int getChargeTicks() {
        return 60; // 3 seconds
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Combustion.gather((ServerLevel) player.level(), player, ticksHeld, getChargeTicks());
    }

    @Override
    public void onChargeCancel(ServerPlayer player, BendingData data) {
        Combustion.misfire(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(1.0));

        BendingProjectiles.launch(player, from, player.getLookAngle(),
                CHARGE.withImpact((hitLevel, at) ->
                        Combustion.detonate(hitLevel, player, at, POWER)));

        Combustion.boom(level, player.position(), 1.3F, 1.0F);
    }
}
