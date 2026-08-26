package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Earth. A spike of ground driven up under something, hard and fast.
 *
 * Earth pillar's violent cousin — the same single column of earth, but tapped rather
 * than held, thrown up in a fraction of the time, tipped with a stalagmite, and gone
 * again five seconds later. It goes up wherever the bender is LOOKING rather than at
 * their feet, because a spike that could only appear an arm's length away would be a
 * defensive ability with a damage number on it.
 *
 * The speed is the whole point. A wall eases up over most of a second, which is fine
 * for cover; a spike doing that could be stepped off before it arrived.
 *
 * It hits harder than its three hearts of ground would suggest, and catches the whole
 * ring of blocks around it rather than only the one it comes up in — an ability aimed
 * at a patch of floor under something that is moving needs the room.
 */
public class EarthSpike implements Ability {

    /** How tall the spike stands, tip included. */
    private static final int HEIGHT = 3;

    /** Blink-quick, against the eight ticks a block of wall takes. */
    private static final int SLIDE_TICKS = 2;

    /** How long it stands before sinking. */
    private static final int STAND_TICKS = 100; // 5 seconds

    /**
     * Four and a half hearts — half again on the three a spike this awkward to land
     * would otherwise be worth. It has to be aimed at a patch of ground under
     * something that is moving, and it announces itself by coming out of the floor.
     */
    private static final float DAMAGE = 9.0F;

    /**
     * How far from the column something can be and still be caught.
     *
     * 1.8 rather than 1.0, so the whole ring of neighbouring blocks counts — a block
     * away diagonally is 1.41 from the centre, and a body standing at the far edge of
     * one is further still. At 1.0 only something standing almost exactly on the spike
     * was hit, which for an ability that is already hard to place was punishing twice.
     */
    private static final double HIT_RADIUS = 1.8;

    /** How far away one can be driven up. */
    private static final double REACH = 20.0;
    private static final int GROUND_SCAN = 20;

    /** How far the ground under the aim point may be hunted for. */
    private static final int UP_SCAN = 2;
    private static final int DOWN_SCAN = 3;

    @Override
    public String getName() {
        return "Earth spike";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    /** Refused for free when there is no ground where the bender is looking. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (surface(player) != null) return true;

        player.displayClientMessage(
                Component.literal("§6There is no ground there to raise!"), true);
        return false;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        BlockPos surface = surface(player);
        if (surface == null) return;

        BlockState earth = EarthWorks.materialUnder(level, surface);

        // A stalagmite tip, which is what makes it read as a spike rather than a post.
        // Pointed dripstone survives anywhere with something solid beneath it, and the
        // earth below is exactly that.
        BlockState tip = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP);

        for (int y = 0; y < HEIGHT; y++) {
            BlockState state = (y == HEIGHT - 1) ? tip : earth;
            EarthWorks.raiseFor(level, surface.above(y), state, STAND_TICKS, SLIDE_TICKS);
        }

        impale(player, level, surface);

        level.playSound(null, surface.getX(), surface.getY(), surface.getZ(),
                SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, earth),
                surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5,
                25, 0.4, 0.2, 0.4, 0.15);
    }

    /**
     * Hurts whatever the spike came up through, or was standing beside when it did.
     *
     * Dealt once, as the spike rises, rather than for as long as it stands — the
     * damage is being caught by something coming out of the ground, not standing near
     * a rock afterwards.
     */
    private static void impale(ServerPlayer player, ServerLevel level, BlockPos surface) {
        Vec3 centre = new Vec3(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);

        AABB column = new AABB(
                centre.x - HIT_RADIUS, centre.y - 0.5, centre.z - HIT_RADIUS,
                centre.x + HIT_RADIUS, centre.y + HEIGHT, centre.z + HIT_RADIUS);

        // Null rather than the caster, so the BENDER is caught too. Passing the player
        // here excludes them from their own search, which is right for a thing you
        // throw and wrong for a thing you put in the ground: a spike is a hazard that
        // comes up wherever it is aimed, and standing on it should hurt.
        for (Entity target : level.getEntities((Entity) null, column, e -> e instanceof LivingEntity)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            // Round, not the square box the search had to use.
            double dx = living.getX() - centre.x;
            double dz = living.getZ() - centre.z;
            if ((dx * dx) + (dz * dz) > HIT_RADIUS * HIT_RADIUS) continue;

            // indirectMagic rather than a physical source: it bypasses armour, so the
            // four and a half hearts land in full on a geared target, and it stays clear
            // of the tags other abilities key off. The same choice Wind and Airpush make.
            living.hurt(player.damageSources().indirectMagic(player, player), DAMAGE);
        }
    }

    /** The ground where the bender is looking, or null if there is none. */
    private static BlockPos surface(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 aim = Aiming.groundUnderLook(player, REACH, GROUND_SCAN);
        return EarthWorks.surfaceUnder(level, BlockPos.containing(aim), UP_SCAN, DOWN_SCAN);
    }
}
