package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerPlayer;

/**
 * Left / Sound. A wall of pure sound two blocks in front, following the crosshair:
 * transparent, made of nothing at all, and still solid enough to stop anything
 * walking through it and to eat anything shot at it.
 *
 * A TOGGLE, so the bender chooses how long to hold it up and pays by the second for
 * doing so. The wall itself lives in {@link SoundWalls}.
 *
 * Its costs were NOT in the design, which gives this ability no numbers whatsoever.
 * They are set in line with its siblings: cheap to hold, no cooldown, and a bank
 * required to raise it, which is the shape the mod already uses for held defences.
 */
public class SoundWall implements Ability {

    /** Registry key. */
    public static final String KEY = "sound wall";

    /** INVENTED: chi drained per second while it stands. */
    public static final int CHI_PER_SECOND = 10;

    /** INVENTED: chi that must already be banked before it will go up. */
    public static final int CHI_TO_START = 100;

    /** INVENTED: xp paid per second while it stands. */
    public static final int XP_PER_SECOND = 1;

    @Override
    public String getName() {
        return "Sound wall";
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

    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return SoundWalls.has(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        SoundWalls.drop(player);
    }

    /** A gate, not a cost: nothing is deducted for meeting it. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getCurrentChi() >= CHI_TO_START;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        SoundWalls.raise(player);
    }
}
