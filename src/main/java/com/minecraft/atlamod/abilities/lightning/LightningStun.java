package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.network.ScreenFlashPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Right / Lightning. A jolt that locks whatever the bender is looking at in place
 * for three seconds. No damage at all — pure control, the way Water push and Air
 * pull are.
 *
 * Three seconds is short, and the twenty-five second cooldown is long, which is
 * deliberate: this is the opener that lets something else land, not a way to keep
 * anything permanently helpless.
 */
public class LightningStun implements ChargedAbility {

    /** The element's one-second wind-up. See Lightning.MINIMUM_CHARGE_TICKS. */
    @Override
    public int getChargeTicks() {
        return Lightning.MINIMUM_CHARGE_TICKS;
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Lightning.gather((ServerLevel) player.level(), player, ticksHeld, getChargeTicks());
    }

    /** How far the jolt reaches, in blocks. */
    private static final double REACH = 12.0;

    /** How far off the crosshair a target may be and still be caught. */
    private static final double TOLERANCE = 2.0;

    /** Key of the upgrade that doubles how long the stun holds. */
    public static final String LASTING_SHOCK = "lightning_stun_lasting_shock";

    /** Three seconds, as specced. Six with Lasting Shock. */
    private static final int STUN_TICKS = 60;

    /** How long the victim's screen stays white. Long enough to register, short enough not to blind. */
    private static final int FLASH_TICKS = 8;

    @Override
    public String getName() {
        return "Lightning stun";
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
        return 500; // 25 seconds
    }

    @Override
    public java.util.List<AbilityUpgrade> getUpgrades() {
        return java.util.List.of(new AbilityUpgrade(
                LASTING_SHOCK,
                "Lasting Shock",
                "The stun holds for twice as long (6 seconds)",
                10));
    }

    /**
     * How long the jolt holds, doubled by Lasting Shock.
     *
     * The cooldown is deliberately NOT touched by the upgrade. Six seconds of hold
     * against twenty-five of cooldown is still a window rather than a lock, which is
     * the shape this ability is meant to keep however much is spent on it.
     */
    private static int stunTicks(BendingData data) {
        return data.hasUpgrade(LASTING_SHOCK) ? STUN_TICKS * 2 : STUN_TICKS;
    }

    /**
     * Refuses the cast when there is nothing in front of the bender.
     *
     * Checked before chi is spent, so aiming at empty air costs nothing — which
     * matters far more here than for most abilities, since twenty-five seconds is a
     * long time to lose to a miss that never went anywhere.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return Aiming.nearestAlongLook(player, REACH, TOLERANCE) != null;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return; // Moved out of the way between the check and here.

        target.addEffect(new MobEffectInstance(ModEffects.STUNNED, stunTicks(data), 0, false, true, true));

        Lightning.visualStrike(level, target.position());
        Lightning.spark(level, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 25, 0.35);
        Lightning.crack(level, target.position(), 1.0F, 1.5F);

        // The white-out only means anything to something with a screen.
        if (target instanceof ServerPlayer victim) {
            PacketDistributor.sendToPlayer(victim, new ScreenFlashPacket(FLASH_TICKS));
        }
    }
}
