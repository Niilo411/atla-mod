package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Rides;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Balanced / Earth. The bender becomes a drill and goes underground.
 *
 * A TOGGLE and a ride, so it steers exactly like Air Scooter and Water Surf: you go
 * where you LOOK, and WASD does nothing. The difference is that this is the one ride
 * where up and down are the rider's to choose — a drill that ignored the vertical half
 * of the look vector could only ever tunnel sideways.
 *
 * It ends by itself the moment the rider breaks back out into open air, which is the
 * natural finish for a tunnel, and it ends if the chi runs out. Running dry is
 * deliberately NOT made safe: the ride simply stops, leaving the bender standing
 * inside solid rock, and vanilla does the rest. Digging deeper than you can pay to get
 * out of is supposed to be a real risk.
 */
public class EarthDig implements Ability {

    /** How far below level the bender has to be looking to start drilling. */
    private static final double DOWNWARD_AIM = -0.35;

    @Override
    public String getName() {
        return "Earth dig";
    }

    /** Paid per second by Rides while it runs, not for the keypress. */
    @Override
    public int getChiCost() {
        return 0;
    }

    /** Earned per second while drilling, not for the keypress. */
    @Override
    public int getXpReward() {
        return 0;
    }

    /**
     * None. A cooldown on a toggle punishes getting OFF, which is the one thing a
     * player should always be able to do immediately — and here that means climbing
     * back out of a hole.
     */
    @Override
    public int getCooldownTicks() {
        return 0;
    }

    /**
     * Has to be aimed downward, and at something solid.
     *
     * Both halves matter. Without the aim test a drill started while looking at the
     * horizon would bore off sideways through a hillside, which is not what the
     * ability is; without the ground test it could be started in mid-air, where it
     * would immediately meet the "back in open air" condition and switch straight off
     * again after taking a tick's chi.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (Rides.isRiding(player)) return false;

        if (player.getLookAngle().y > DOWNWARD_AIM) {
            player.displayClientMessage(
                    Component.literal("§6Look down at the ground to dig!"), true);
            return false;
        }

        if (!solidBelow(player)) {
            player.displayClientMessage(
                    Component.literal("§6There is no ground here to dig into!"), true);
            return false;
        }

        return true;
    }

    /** Drilling already: the next press stops. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return Rides.isRiding(player, Rides.Kind.EARTH_DIG);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        Rides.stop(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        if (!Rides.start(player, Rides.Kind.EARTH_DIG, null)) return;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /** Whether there is something to dig into just under the bender's feet. */
    private static boolean solidBelow(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;

        Vec3 pos = player.position();
        BlockPos under = BlockPos.containing(pos.x, pos.y - 0.5, pos.z);

        return level.getBlockState(under).isSolid();
    }
}
