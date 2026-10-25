package io.github.niilo411.elementalpaths.abilities.lava;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.abilities.Ability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lava. Lava runs out along the ground in the direction the channeler is looking,
 * pouring down slopes and stopping dead at anything it cannot climb.
 *
 * The element's opening ability. It does no damage of its own at all — everything it
 * does is done by the lava it leaves, which is the shape most of lava channeling takes:
 * the channeler puts the hazard somewhere, and what happens next is up to whoever is
 * standing in it.
 *
 * The aim is FLATTENED, so a cast made while looking at the sky still runs along the
 * ground rather than off into the air.
 */
public class LavaRiver implements Ability {

    /** How far in front of the channeler the river starts, so it is not under their feet. */
    private static final double OFFSET = 1.5;

    @Override
    public String getName() {
        return "Lava river";
    }

    @Override
    public int getChiCost(ChannelingData data) {
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
    public void execute(ServerPlayer player, ChannelingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 forward = Lava.flatLook(player);
        Vec3 origin = player.position().add(forward.scale(OFFSET));

        LavaRivers.start(level, origin, forward);
    }
}
