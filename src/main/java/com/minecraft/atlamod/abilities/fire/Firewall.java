package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Defensive / Fire. Lays a 6-block wall of fire across the ground in front of
 * the player — a barrier to put between yourself and something, so it runs
 * perpendicular to where you are looking rather than away from you.
 */
public class Firewall implements Ability {

    /** Wall length in blocks. */
    private static final int LENGTH = 6;

    /** How far in front of the player the wall is laid, in blocks. */
    private static final double DISTANCE = 2.0;

    /** How far up and down to look for ground, so the wall follows a slope or a step. */
    private static final int UP_SCAN = 1;
    private static final int DOWN_SCAN = 3;

    @Override
    public String getName() {
        return "Firewall";
    }

    @Override
    public int getChiCost() {
        return 30;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 20; // 1 second
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Flatten the look vector: the wall lies on the ground regardless of
        // whether the player is looking at the sky or their feet.
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            // Looking straight up or down — fall back to the way the body faces.
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        flat = flat.normalize();

        // Perpendicular in the horizontal plane, so the wall spans across the
        // player's facing rather than stretching away from them.
        Vec3 across = new Vec3(-flat.z, 0.0, flat.x);
        Vec3 origin = player.position().add(flat.scale(DISTANCE));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.8F);

        for (int i = 0; i < LENGTH; i++) {
            // Centre the run on the player: offsets go -2.5 .. 2.5 for a length of 6.
            double offset = i - (LENGTH - 1) / 2.0;
            Vec3 spot = origin.add(across.scale(offset));

            placeFire(level, BlockPos.containing(spot));
        }
    }

    /**
     * Drops one fire block at the first air space with solid ground under it,
     * scanning a little up and down from the target so the wall tracks terrain.
     * Only ever replaces air, so casting this never destroys anything.
     */
    private static void placeFire(ServerLevel level, BlockPos target) {
        for (int dy = UP_SCAN; dy >= -DOWN_SCAN; dy--) {
            BlockPos pos = target.above(dy);

            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid()) {
                level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
                level.sendParticles(ParticleTypes.FLAME,
                        pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                        6, 0.2, 0.2, 0.2, 0.01);
                return;
            }
        }
    }
}
