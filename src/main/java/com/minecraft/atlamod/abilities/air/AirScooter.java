package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerPlayer;

/**
 * Balanced / Air. A ball of air under the rider, carrying them where they look at a
 * run, half a block off the ground.
 *
 * A TOGGLE, not a held ability, and that is why it is a plain {@link Ability} rather
 * than a ChanneledAbility: press to get on, press again to get off. The click
 * dispatch (UseAbilityPacket) fires once per press, which is exactly what a toggle
 * wants, where the hold dispatch built for Fire Breath reports key state instead.
 *
 * Almost nothing lives in this class. The ride owns an entity and has to be taken
 * down safely from half a dozen different directions, so it lives in
 * {@link AirScooters} alongside the other per-player managers — and the running cost
 * is charged there too, since the dispatcher only knows how to bill held abilities
 * and this is not one.
 */
public class AirScooter implements Ability {

    /** Doubles how fast the scooter travels. Read by AirScooters when steering. */
    public static final String SLIPSTREAM = "air_scooter_slipstream";
    private static final int SLIPSTREAM_COST = 10;

    @Override
    public String getName() {
        return "Air scooter";
    }

    @Override
    public java.util.List<com.minecraft.atlamod.abilities.AbilityUpgrade> getUpgrades() {
        return java.util.List.of(new com.minecraft.atlamod.abilities.AbilityUpgrade(
                SLIPSTREAM,
                "Slipstream",
                "The scooter carries you twice as fast",
                SLIPSTREAM_COST));
    }

    /**
     * Free to get on and off. The 5 chi a second is charged by AirScooters while the
     * ride runs; billing anything here would be a toll on the keypress instead.
     */
    @Override
    public int getChiCost() {
        return 0;
    }

    /** Earned per second while riding, not for pressing the key. */
    @Override
    public int getXpReward() {
        return 0;
    }

    /**
     * None. A cooldown on a toggle punishes getting OFF, which is the one thing a
     * player should always be able to do immediately.
     */
    @Override
    public int getCooldownTicks() {
        return 0;
    }

    /** Riding already: the next press gets off. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return AirScooters.isRiding(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        AirScooters.stop(player);
    }

    /** Press on. Getting off goes through deactivate, not through here. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        AirScooters.start(player);
    }
}
