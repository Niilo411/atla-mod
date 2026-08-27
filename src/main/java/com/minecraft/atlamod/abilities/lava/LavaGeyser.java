package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lava. Three geysers, set down one per left click wherever the bender is
 * looking, each spitting lava for twenty seconds.
 *
 * Air spout's shape, and for the same reason: "create 3" wants three PLACED things, and
 * a cast that dropped all three at once would bunch them within a couple of blocks of
 * each other and give the bender no say in where any of them went. Arming the slot and
 * spending one per click makes it three decisions instead of none.
 *
 * The armed state waits indefinitely, like Fireball and Water ball. The chi was spent
 * summoning them and the cooldown does not start until the LAST one is put down, so
 * there is nothing to be gained by holding them and nothing lost by taking your time.
 *
 * Once down, a geyser is nobody's — see {@link LavaGeysers}.
 */
public class LavaGeyser implements TwoPhaseAbility {

    /** How far away one can be set down, in blocks. INVENTED — the design says none. */
    private static final double REACH = 20.0;

    /** How far below the end of the look to hunt for ground. */
    private static final int GROUND_SCAN = 20;

    /** How far up and down the chosen spot looks for footing. */
    private static final int UP_SCAN = 2;
    private static final int DOWN_SCAN = 3;

    @Override
    public String getName() {
        return "Lava geyser";
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
        return 600; // 30 seconds, starting from the LAST geyser
    }

    @Override
    public int getShots() {
        return 3; // the design's three
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.displayClientMessage(Component.literal(
                "§6Three geysers ready — left click to set one down"), true);
    }

    /** Shown in the bender's hands while the geysers are waiting to be placed. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (player.tickCount % 3 != 0) return;

        Vec3 hands = player.getEyePosition().add(player.getLookAngle().scale(0.7));
        Lava.spatter((ServerLevel) player.level(), hands, 2, 0.2);
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Falls back to the ground under the end of the look rather than refusing: the
        // dispatcher counts the CLICK, not the outcome, so the shot is already spent by
        // the time this runs and aiming at open sky must put a geyser SOMEWHERE. Same
        // reasoning as Air spout.
        Vec3 aim = Aiming.groundUnderLook(player, REACH, GROUND_SCAN);

        BlockPos ground = Lava.footing(level, BlockPos.containing(aim), UP_SCAN, DOWN_SCAN);
        if (ground == null) {
            player.displayClientMessage(Component.literal(
                    "§7No ground there for a geyser."), true);
            return;
        }

        LavaGeysers.place(level, ground);
    }
}
