package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lava. A wall of lava nine blocks across and four high rolls thirty blocks out
 * in front of the bender, hitting everything it washes over and carrying it along.
 *
 * Waterbending's Tsunami with the material swapped, and the material is the whole
 * difference: water hits once and leaves, where this hits once, sets everything it
 * touched alight, and then keeps burning them for as long as they are inside it. The
 * four hp the design gives it is the BONUS on top of that, not the ability's damage.
 *
 * The wave takes its own lava up behind itself as it travels — see {@link LavaTsunamis}.
 */
public class LavaTsunami implements ChargedAbility {

    /** How far it rolls, in blocks. The design's thirty. */
    private static final int RANGE = 30;

    /** The design's four hp of bonus damage, on top of the burning. */
    private static final float BONUS_DAMAGE = 4.0F;

    /** How far in front it starts, so the bender is not inside their own wave. */
    private static final double OFFSET = 3.0;

    @Override
    public String getName() {
        return "Lava tsunami";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 300;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    @Override
    public int getCooldownTicks() {
        return 800; // 40 seconds
    }

    /**
     * Five seconds of gathering before the wave goes anywhere.
     *
     * The heaviest thing in the element and now the slowest to start, which is the
     * point: thirty blocks of moving lava should be something anyone nearby can see
     * coming and get out of the way of. Letting go early simply cancels, and costs
     * nothing — this is not combustion, and there is no misfire in lavabending.
     */
    @Override
    public int getChargeTicks() {
        return 100; // 5 seconds
    }

    /** The ground shaking itself loose, building as the wave gathers. */
    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 ahead = player.position().add(Lava.flatLook(player).scale(OFFSET));

        Lava.spatter(level, ahead, 4 + (int) (progress * 12), 1.0 + (progress * 3.0));

        // Once a second, so five seconds of holding still has a shape to it.
        if (ticksHeld % 20 == 0) {
            Lava.roar(level, player.position(), 1.2F, 0.5F + (float) progress * 0.4F);
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Vec3 forward = Lava.flatLook(player);
        Vec3 origin = player.position().add(forward.scale(OFFSET));

        LavaTsunamis.launch(player, origin, forward, RANGE,
                Lava.damage(data, BONUS_DAMAGE));
    }
}
