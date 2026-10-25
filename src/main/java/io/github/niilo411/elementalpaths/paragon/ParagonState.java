package io.github.niilo411.elementalpaths.paragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The Paragon cycle's own state, which belongs to the WORLD rather than to any
 * player: whether the cycle is running, which element it has reached, and who is
 * currently the Paragon.
 *
 * Held as a NeoForge attachment on the overworld — the same mechanism ChannelingData
 * uses, with a level as the holder instead of a player, so it serialises itself
 * with the world and needs no SavedData of its own.
 *
 * The current Paragon is tracked by UUID here as well as by the flag on their own
 * ChannelingData. That is deliberate duplication: the flag is what every check reads
 * (it is on the player, where it is cheap), and the UUID is what lets the cycle
 * revoke a Paragon who is OFFLINE — the flag on a player who is not logged in
 * cannot be reached at all.
 */
public class ParagonState {

    /** The order the cycle moves through, starting at earth. */
    public static final String[] CYCLE = { "earth", "fire", "air", "water" };

    private boolean cycleRunning = false;
    private int cycleIndex = 0;
    private String paragonId = "";

    public static final Codec<ParagonState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("cycleRunning", false).forGetter(ParagonState::isCycleRunning),
            Codec.INT.optionalFieldOf("cycleIndex", 0).forGetter(ParagonState::getCycleIndex),
            Codec.STRING.optionalFieldOf("paragonId", "").forGetter(ParagonState::getParagonId)
    ).apply(instance, (running, index, id) -> {
        ParagonState state = new ParagonState();
        state.cycleRunning = running;
        state.setCycleIndex(index);
        state.setParagonId(id);
        return state;
    }));

    public boolean isCycleRunning() { return cycleRunning; }
    public void setCycleRunning(boolean running) { this.cycleRunning = running; }

    public int getCycleIndex() { return cycleIndex; }

    /** Wrapped rather than clamped, so the cycle comes back round to earth. */
    public void setCycleIndex(int index) {
        this.cycleIndex = Math.floorMod(index, CYCLE.length);
    }

    /** The element the cycle is looking for a Paragon among right now. */
    public String getCycleElement() {
        return CYCLE[cycleIndex];
    }

    public void advanceCycle() {
        setCycleIndex(this.cycleIndex + 1);
    }

    /** The current Paragon's UUID as a string, or empty when there is nobody. */
    public String getParagonId() { return paragonId == null ? "" : paragonId; }
    public void setParagonId(String id) { this.paragonId = id == null ? "" : id; }

    public boolean hasParagon() { return !getParagonId().isEmpty(); }
}
