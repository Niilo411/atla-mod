package com.minecraft.atlamod.avatar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The Avatar cycle's own state, which belongs to the WORLD rather than to any
 * player: whether the cycle is running, which element it has reached, and who is
 * currently the Avatar.
 *
 * Held as a NeoForge attachment on the overworld — the same mechanism BendingData
 * uses, with a level as the holder instead of a player, so it serialises itself
 * with the world and needs no SavedData of its own.
 *
 * The current Avatar is tracked by UUID here as well as by the flag on their own
 * BendingData. That is deliberate duplication: the flag is what every check reads
 * (it is on the player, where it is cheap), and the UUID is what lets the cycle
 * revoke an Avatar who is OFFLINE — the flag on a player who is not logged in
 * cannot be reached at all.
 */
public class AvatarState {

    /** The order the cycle moves through, starting at earth. */
    public static final String[] CYCLE = { "earth", "fire", "air", "water" };

    private boolean cycleRunning = false;
    private int cycleIndex = 0;
    private String avatarId = "";

    public static final Codec<AvatarState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("cycleRunning", false).forGetter(AvatarState::isCycleRunning),
            Codec.INT.optionalFieldOf("cycleIndex", 0).forGetter(AvatarState::getCycleIndex),
            Codec.STRING.optionalFieldOf("avatarId", "").forGetter(AvatarState::getAvatarId)
    ).apply(instance, (running, index, id) -> {
        AvatarState state = new AvatarState();
        state.cycleRunning = running;
        state.setCycleIndex(index);
        state.setAvatarId(id);
        return state;
    }));

    public boolean isCycleRunning() { return cycleRunning; }
    public void setCycleRunning(boolean running) { this.cycleRunning = running; }

    public int getCycleIndex() { return cycleIndex; }

    /** Wrapped rather than clamped, so the cycle comes back round to earth. */
    public void setCycleIndex(int index) {
        this.cycleIndex = Math.floorMod(index, CYCLE.length);
    }

    /** The element the cycle is looking for an Avatar among right now. */
    public String getCycleElement() {
        return CYCLE[cycleIndex];
    }

    public void advanceCycle() {
        setCycleIndex(this.cycleIndex + 1);
    }

    /** The current Avatar's UUID as a string, or empty when there is nobody. */
    public String getAvatarId() { return avatarId == null ? "" : avatarId; }
    public void setAvatarId(String id) { this.avatarId = id == null ? "" : id; }

    public boolean hasAvatar() { return !getAvatarId().isEmpty(); }
}
