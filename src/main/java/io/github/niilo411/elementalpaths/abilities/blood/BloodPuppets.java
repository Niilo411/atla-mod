package io.github.niilo411.elementalpaths.abilities.blood;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who each blood channeler currently has hold of.
 *
 * Deliberately tiny, and deliberately NOT a ticking tracker like the rest of the mod's
 * managers: a puppet lives exactly as long as the channel holding it, so
 * {@link BloodManipulation} drives it from its own onTick and this only has to
 * remember who is holding whom.
 *
 * The grip is stored rather than re-aimed each tick because that is the whole point of
 * the ability — a puppet dropped by glancing away would be unusable for the thing it
 * exists to do. Blood Slow is the one that re-aims.
 */
public final class BloodPuppets {

    private static final Map<UUID, LivingEntity> HELD = new HashMap<>();

    private BloodPuppets() {
    }

    /** Takes hold of a victim. Any previous grip is dropped first. */
    public static void take(ServerLevel level, ServerPlayer channeler, LivingEntity victim) {
        HELD.put(channeler.getUUID(), victim);
    }

    /** Who this channeler has hold of, or null. */
    public static LivingEntity of(ServerPlayer channeler) {
        LivingEntity puppet = HELD.get(channeler.getUUID());

        // Dropped if it has died or been removed since — a stale reference here would
        // keep a dead entity reachable for as long as the channeler stayed logged in.
        if (puppet != null && !puppet.isAlive()) {
            HELD.remove(channeler.getUUID());
            return null;
        }
        return puppet;
    }

    /** Lets go. */
    public static void release(ServerPlayer channeler) {
        HELD.remove(channeler.getUUID());
    }

    /** Called on death, logout and dimension change — for the channeler AND the puppet. */
    public static void forgetPlayer(ServerPlayer player) {
        HELD.remove(player.getUUID());
        HELD.values().removeIf(puppet -> puppet == player);
    }

    public static void forgetLevel(ServerLevel level) {
        HELD.values().removeIf(puppet -> puppet.level() == level);
    }
}
