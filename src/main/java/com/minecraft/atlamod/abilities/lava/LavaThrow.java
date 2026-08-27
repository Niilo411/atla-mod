package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Lava. Two seconds of gathering, then four blobs of lava hurled down the
 * crosshair — and where they land, the lava STAYS.
 *
 * The one ability in the element that does not go through {@link LavaWorks}, and the
 * only one that leaves anything behind at all. The design says "permanent" in so many
 * words, so this places real vanilla lava: it flows, it spreads, it sets things alight
 * and nothing will ever take it away again. Everything else lavabending does is
 * borrowed; this is the ability that actually changes the world, and it is priced and
 * limited accordingly — four blocks, and five seconds before another four.
 *
 * Real lava also means the blobs only ever land in AIR. Overwriting whatever they hit
 * would make a five second cooldown into a demolition tool.
 */
public class LavaThrow implements ChargedAbility {

    /** How many blobs go out per cast. The design's four. */
    private static final int COUNT = 4;

    /**
     * How far apart along the line they leave, in blocks.
     *
     * Spaced rather than fired on a timer, which is the same trick Combustion
     * bombardment uses: four shots "towards where you look" arrive strung out behind
     * one another without the ability having to keep a countdown alive after the cast.
     */
    private static final double SPACING = 1.2;

    private static final BendingProjectiles.Spec BLOB = new BendingProjectiles.Spec(
            1.6, 80, 6.0F, 0.9, 0.3, BendingProjectiles.Style.LAVA);

    @Override
    public String getName() {
        return "Lava throw";
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
        return 100; // 5 seconds
    }

    @Override
    public int getChargeTicks() {
        return 40; // the design's 2 second charge
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Vec3 hands = player.getEyePosition().add(player.getLookAngle().scale(0.8));

        // Tightens as it fills, so the bender can see the blobs gathering.
        double spread = 0.6 - (0.45 * (ticksHeld / (double) getChargeTicks()));
        Lava.spatter((ServerLevel) player.level(), hands, 3, spread);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();

        for (int i = 0; i < COUNT; i++) {
            Vec3 from = eye.add(look.scale(1.0 - (i * SPACING)));

            BendingProjectiles.launch(player, from, look,
                    BLOB.withImpact(LavaThrow::settle));
        }

        Lava.roar(level, player.position(), 1.2F, 1.1F);
    }

    /**
     * Sets one blob down as real, permanent lava.
     *
     * Only into air — a blob that replaced whatever it struck would turn a five second
     * cooldown into a way of deleting somebody's wall four blocks at a time. Landing
     * against something solid therefore does nothing but burn, which is the honest
     * outcome: the lava hit the wall rather than becoming it.
     */
    private static void settle(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);

        if (level.getBlockState(pos).isAir()) {
            level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        }

        Lava.spatter(level, at, 12, 0.5);
        Lava.hiss(level, at, 1.0F, 0.8F);
    }
}
