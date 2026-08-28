package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Earth. The ground opens, everything standing on it drops in, and then
 * the ground closes again over the top.
 *
 * Ravine's cleverer sibling. That one simply takes the world apart and leaves it that
 * way; this one BORROWS it — the same pit, but every block is remembered and put back
 * a few seconds later. What is left afterwards is a landscape exactly as it was, and
 * whatever fell in is now inside it.
 *
 * That burial is the real weapon. The blow on cast is only an opener: the thing that
 * kills is being three blocks down when the roof comes back, with vanilla suffocation
 * doing the rest and no way out but digging.
 */
public class EarthSink implements Ability {

    /** How far ahead the pit reaches. */
    private static final int LENGTH = 12;

    /** How wide it opens. Even, so the run is centred between two columns. */
    private static final int WIDTH = 6;

    /** How far down it goes, measured from each column's own surface. */
    private static final int DEPTH = 7;

    /**
     * How long the ground stays open.
     *
     * Long enough to fall seven blocks and no longer — the pit is a trap, not a
     * quarry, and a victim who has time to climb out has beaten it fairly.
     */
    private static final int OPEN_TICKS = 60;

    /** The blow on the way in, before anything is buried. */
    private static final float DAMAGE = 8.0F;

    /** How far each column hunts for its own surface, so the pit follows the ground. */
    private static final int UP_SCAN = 3;
    private static final int DOWN_SCAN = 4;

    @Override
    public String getName() {
        return "Earth sink";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 250;
    }

    @Override
    public int getXpReward() {
        return 25;
    }

    @Override
    public int getCooldownTicks() {
        return 2000; // 100 seconds
    }

    /** Refused for free when there is no ground in front to open. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 spot = player.position().add(facing(player).scale(2.0));
        if (EarthWorks.surfaceUnder(level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN) != null) {
            return true;
        }

        player.displayClientMessage(
                Component.literal("§6There is no ground here to open!"), true);
        return false;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 forward = facing(player);
        Vec3 across = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 origin = player.position();

        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 2.0F, 0.3F);

        strike(player, level, origin, forward);

        // Starts a block out rather than underfoot, so a bender does not drop into
        // their own pit the moment it opens.
        for (int distance = 1; distance <= LENGTH; distance++) {
            for (int i = 0; i < WIDTH; i++) {
                // Centre an even run between two columns: offsets -2.5 .. 2.5 for six.
                double offset = i - (WIDTH - 1) / 2.0;

                Vec3 spot = origin
                        .add(forward.scale(distance))
                        .add(across.scale(offset));

                openColumn(level, BlockPos.containing(spot));
            }
        }
    }

    /**
     * Hurts everything standing over the pit as it opens.
     *
     * Dealt before the ground goes, while victims are still where the ability was
     * aimed — a moment later they are falling, and a box drawn around the surface
     * would start missing them.
     */
    private static void strike(ServerPlayer player, ServerLevel level, Vec3 origin, Vec3 forward) {
        Vec3 centre = origin.add(forward.scale(LENGTH / 2.0));

        AABB area = new AABB(centre, centre)
                .inflate(LENGTH / 2.0 + 1.0, DEPTH, LENGTH / 2.0 + 1.0);

        for (Entity target : level.getEntities(player, area)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            living.hurt(player.damageSources().indirectMagic(player, player), DAMAGE);
        }
    }

    /**
     * Opens one column, from its own surface downward.
     *
     * Every block is handed to EarthWorks rather than simply destroyed, which is what
     * makes this Earth sink and not a wider Ravine: it remembers each one and closes
     * the ground back over the hole when the time is up.
     */
    private static void openColumn(ServerLevel level, BlockPos target) {
        BlockPos surface = EarthWorks.surfaceUnder(level, target, UP_SCAN, DOWN_SCAN);
        if (surface == null) return;

        BlockState top = level.getBlockState(surface.below());

        for (int depth = 1; depth <= DEPTH; depth++) {
            EarthWorks.openFor(level, surface.below(depth), OPEN_TICKS);
        }

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top),
                surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5,
                10, 0.4, 0.3, 0.4, 0.1);
    }

    /**
     * Where the bender is facing, flattened onto the ground.
     *
     * The pit opens along the ground whether they are looking at the sky or their feet,
     * and looking straight up or down falls back to the way the body is turned rather
     * than leaving it with no direction at all.
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
