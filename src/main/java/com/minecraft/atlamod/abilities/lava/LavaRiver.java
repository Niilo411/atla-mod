package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lava. Lava runs out along the ground in the direction the bender is looking,
 * pouring down slopes and stopping dead at anything it cannot climb.
 *
 * The element's opening ability. It does no damage of its own at all — everything it
 * does is done by the lava it leaves, which is the shape most of lavabending takes:
 * the bender puts the hazard somewhere, and what happens next is up to whoever is
 * standing in it.
 *
 * The aim is FLATTENED, so a cast made while looking at the sky still runs along the
 * ground rather than off into the air.
 */
public class LavaRiver implements Ability {

    /** How far in front of the bender the river starts, so it is not under their feet. */
    private static final double OFFSET = 1.5;

    @Override
    public String getName() {
        return "Lava river";
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
        return 400; // 20 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 forward = Lava.flatLook(player);
        Vec3 origin = player.position().add(forward.scale(OFFSET));

        LavaRivers.start(level, origin, forward);
    }
}
