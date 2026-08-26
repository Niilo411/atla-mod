package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Ice. Five seconds of gathering, and then the sky comes down: huge icicles
 * falling across a thirty block radius, twenty hp each where they land.
 *
 * Built out of falling PROJECTILES rather than a tracker of its own, which is what
 * the generic onImpact hook on BendingProjectiles.Spec is for. Each icicle is an
 * ordinary shot launched straight down from high above: the projectile system already
 * sweeps its path (so it cannot fall through a roof), already carries the damage, and
 * already calls back wherever it stops — on a target, on the ground, or at the end of
 * its life. All this ability adds is what to plant there.
 */
public class IceBarrage implements ChargedAbility {

    /** How wide the barrage falls, in blocks from the bender. */
    private static final double RADIUS = 30.0;

    /** How many icicles come down. */
    private static final int COUNT = 40;

    /** 20 hp each, as specced. */
    private static final float DAMAGE = 20.0F;

    /** How far above the bender the icicles start. */
    private static final int DROP_HEIGHT = 25;

    /** How long a landed icicle stands before melting. */
    private static final int STAND_TICKS = 200; // 10 seconds

    private static final BendingProjectiles.Spec ICICLE = new BendingProjectiles.Spec(
            1.6, 80, DAMAGE, 1.0, 0.1, BendingProjectiles.Style.ICE);

    @Override
    public String getName() {
        return "Ice barrage";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 300;
    }

    @Override
    public int getXpReward() {
        return 30;
    }

    @Override
    public int getCooldownTicks() {
        return 400; // 20 seconds
    }

    @Override
    public int getChargeTicks() {
        return 100; // 5 seconds
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        // Gathering overhead rather than in the hands, because that is where it is
        // about to come from — the wind-up tells everyone nearby to move.
        double progress = ticksHeld / (double) getChargeTicks();
        Ice.frost(level, player.position().add(0.0, 4.0 + progress * 3.0, 0.0),
                4 + (int) (progress * 20), 2.0 + progress * 6.0);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 centre = player.position();

        BendingProjectiles.Spec icicle = new BendingProjectiles.Spec(
                ICICLE.speed(), ICICLE.lifetime(), Ice.damage(data, DAMAGE),
                ICICLE.hitRadius(), ICICLE.knockback(), ICICLE.style())
                .withImpact(IceBarrage::plant);

        for (int i = 0; i < COUNT; i++) {
            // Scattered by area rather than by radius: picking a uniform radius would
            // bunch every barrage around the bender, since a ring at r=30 holds far
            // more ground than one at r=3. The square root spreads them evenly.
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(level.random.nextDouble()) * RADIUS;

            Vec3 from = centre.add(
                    Math.cos(angle) * distance,
                    DROP_HEIGHT,
                    Math.sin(angle) * distance);

            BendingProjectiles.launch(player, from, new Vec3(0.0, -1.0, 0.0), icicle);
        }

        Ice.crack(level, centre, 1.6F, 0.6F);
    }

    /**
     * Plants a landed icicle: two blocks of ice with a dripstone tip beneath them.
     *
     * Everything goes through IceWorks, so it only ever fills air and takes itself
     * back — a barrage that left forty permanent spikes across a thirty block radius
     * would redecorate the landscape every twenty seconds.
     */
    private static void plant(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);

        // The two blocks of ice go FIRST, and the order is load-bearing: a
        // downward-pointing dripstone needs something solid ABOVE it to hang from, so
        // a tip placed while that space was still air breaks itself the instant it
        // lands and the icicle comes out headless.
        for (int dy = 1; dy <= 2; dy++) {
            IceWorks.freeze(level, base.above(dy), Blocks.PACKED_ICE.defaultBlockState(), STAND_TICKS);
        }

        // The tip hangs beneath them, which is what makes the whole thing read as an
        // icicle rather than a pillar.
        IceWorks.freeze(level, base,
                Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                        .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP),
                STAND_TICKS);

        Ice.shatter(level, at, 20, 0.4);
    }
}
