package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Balanced / Earth. A wall of ground rises twenty blocks out and rolls back in,
 * bringing whatever it washes over with it.
 *
 * Tsunami inverted, and built on the same moving-body machinery — see
 * {@link EarthGrabs}. Where the water version throws things away from the bender, this
 * one hauls them towards, which is what makes it a grab rather than a push. Nothing is
 * damaged by it at all: it is pure displacement, and what the bender does with a mob
 * suddenly deposited at their feet is up to them.
 *
 * The wall is made of whatever the ground is where it starts, so it comes out of the
 * landscape rather than arriving from nowhere.
 */
public class EarthGrab implements Ability {

    /** Where the wave starts, and where it stops — both measured forward. */
    private static final int FROM = 20;
    private static final int TO = 5;

    /** How far each column hunts for its own footing when sampling the far ground. */
    private static final int UP_SCAN = 3;
    private static final int DOWN_SCAN = 4;

    @Override
    public String getName() {
        return "Earth grab";
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
        return 400; // 20 seconds
    }

    /**
     * Refused for free when there is nothing out there to raise a wall from.
     *
     * Water is the named exception: a wave of it would be a waterbender's job, and
     * sampling it would have the ability quietly turn into Tsunami's poor cousin.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos ground = farGround(player);

        if (ground == null) {
            player.displayClientMessage(
                    Component.literal("§6There is no ground out there to grab with!"), true);
            return false;
        }

        if (!level.getFluidState(ground.below()).isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§6You cannot raise a wave out of water!"), true);
            return false;
        }

        return true;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        BlockPos ground = farGround(player);
        if (ground == null) return;

        // Whatever the far ground is made of, sanitised the way all raised earth is —
        // so a wave off a beach is dirt rather than a wall of sand that falls apart.
        BlockState material = EarthWorks.materialUnder(level, ground);

        EarthGrabs.launch(player, player.position(), facing(player), material, FROM, TO);
    }

    /** The ground at the far end, where the wave will be raised from. */
    private static BlockPos farGround(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 spot = player.position().add(facing(player).scale(FROM));
        return EarthWorks.surfaceUnder(level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN);
    }

    /**
     * Where the bender is facing, flattened onto the ground.
     *
     * The wave rolls along the ground whether they are looking at the sky or their
     * feet, and looking straight up or down falls back to the way the body is turned
     * rather than leaving the wave with no direction at all.
     */
    private static Vec3 facing(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);

        if (flat.lengthSqr() < 1.0E-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }

        return flat.normalize();
    }
}
