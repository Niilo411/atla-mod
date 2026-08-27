package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Lava. Five seconds of gathering, then the sky opens over a thirty by thirty
 * patch of ground and rains lava on it for twenty seconds.
 *
 * The element's last ability and its most expensive, and the shape is Fire Rain's: cast
 * once, then run by a tracker for the duration rather than held as a channel. See
 * {@link LavaRains} for why it stays where it was called down instead of following the
 * bender.
 *
 * It does NOT spare its caster's feet. The drops themselves skip the owner — that falls
 * out of how projectiles pick their targets — but the lava they leave behind is lava,
 * and a bender standing in the middle of their own storm will burn in it exactly like
 * everybody else. Which is what {@link LavaResistance} is for.
 */
public class LavaRain implements ChargedAbility {

    @Override
    public String getName() {
        return "lava rain";
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
        return 1000; // 50 seconds
    }

    @Override
    public int getChargeTicks() {
        return 100; // the design's 5 second charge
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.displayClientMessage(Component.literal("§6Calling the sky down..."), true);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        // Drawn overhead rather than in the hands: what is being gathered is the sky,
        // and the bender should be able to see where it is going to fall.
        Vec3 above = player.position().add(0.0, 3.0 + (ticksHeld / 12.0), 0.0);
        Lava.spatter(level, above, 6, 1.5 + (ticksHeld / 25.0));

        // Once a second, so it reads as a countdown rather than as a drone.
        if (ticksHeld % 20 == 0) {
            Lava.roar(level, player.position(), 1.0F, 0.7F + (ticksHeld / 200.0F));
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        LavaRains.call(player);

        player.displayClientMessage(Component.literal("§cThe sky is falling!"), true);
    }
}
