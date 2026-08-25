package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.Rides;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Balanced / Water. Ride the surface of the water, carried wherever you look.
 *
 * Air Scooter's twin, and built on the same {@link Rides} machinery — press once to
 * get on, press again to get off, and while it lasts the bender goes where their
 * crosshair points at a run. The only real difference is the surface: this one sits
 * ON the waterline rather than a block above the ground, and it ends when the water
 * does instead of when the water starts.
 *
 * The bender rides STANDING rather than seated, which the seat carries as a flag —
 * an airbender perched on a ball of air is one thing, but nobody surfs sitting down.
 *
 * The ride is a real entity carrying the player, which is what makes it smooth: a
 * passenger is moved by its vehicle, so the server never has to correct the client
 * about where the player is. Pinning them to the waterline every tick instead is what
 * the old version could not avoid, and what rubber-bands.
 */
public class WaterSurf implements Ability {

    /** Key for the upgrade that quickens the surf. */
    public static final String SWIFT_CURRENT = "water_surf_swift";

    /** What the upgrade costs, in levels. */
    private static final int SWIFT_CURRENT_COST = 10;

    @Override
    public String getName() {
        return "Water Surf";
    }

    /** Paid per second by Rides while it runs, not for the keypress. */
    @Override
    public int getChiCost() {
        return 0;
    }

    /** Earned per second while surfing, not for the keypress. */
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

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(new AbilityUpgrade(
                SWIFT_CURRENT,
                "Swift Current",
                "The surf carries you twice as fast",
                SWIFT_CURRENT_COST));
    }

    /**
     * Deliberately NOT gated on the canteen, for the same reason as the other water
     * abilities that name their own source: this one is riding the water itself.
     */
    @Override
    public boolean requiresWater() {
        return false;
    }

    /** Surfing already: the next press gets off. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return Rides.isRiding(player, Rides.Kind.WATER_SURF);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        Rides.stop(player);
    }

    /** Press on. Getting off goes through deactivate, not through here. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        if (!Rides.start(player, Rides.Kind.WATER_SURF, SWIFT_CURRENT)) return;

        player.displayClientMessage(Component.literal("§bYou ride the surface."), true);
    }
}
