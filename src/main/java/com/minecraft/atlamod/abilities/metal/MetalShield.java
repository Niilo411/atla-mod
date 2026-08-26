package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerPlayer;

/**
 * Left / Metal. A wall of real iron two blocks in front, following the crosshair
 * wherever the bender looks or walks — and a left click sends it flying.
 *
 * Sound wall's heavier twin, and priced identically at the design's request. The
 * difference is what it is made of: sound is particles that shove things back, this is
 * REAL unbreakable blocks that collide the way any wall does.
 *
 * Both a TOGGLE and a TWO-PHASE, which is a pairing nothing else in the mod uses. The
 * slot key raises and lowers it; the left click throws it. Arming happens at cast, so
 * the click is available for as long as the shield is up.
 */
public class MetalShield implements TwoPhaseAbility {

    /** Registry key. */
    public static final String KEY = "metal shield";

    /** Same as Sound wall, as the design asks. */
    public static final int CHI_PER_SECOND = 10;
    public static final int CHI_TO_START = 100;
    public static final int XP_PER_SECOND = 1;

    @Override
    public String getName() {
        return "Metal shield";
    }

    /** Nothing up front: billed by the second from the player tick. */
    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    /** Up already: the next press takes it down. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return MetalShields.has(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        MetalShields.drop(player);
    }

    /** A gate, not a cost: nothing is deducted for meeting it. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getCurrentChi() >= CHI_TO_START;
    }

    /** Held for as long as the shield stands. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    /**
     * Nothing is drawn: the shield is REAL blocks and is already perfectly visible.
     * Particles on top of it would only make the thing that is actually there harder
     * to see — the same call Ice Bomb makes.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        MetalShields.hurl(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        MetalShields.raise(player);
    }
}
