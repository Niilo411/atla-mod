package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
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
public class LavaTsunami implements Ability {

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

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Vec3 forward = Lava.flatLook(player);
        Vec3 origin = player.position().add(forward.scale(OFFSET));

        LavaTsunamis.launch(player, origin, forward, RANGE,
                Lava.damage(data, BONUS_DAMAGE));
    }
}
