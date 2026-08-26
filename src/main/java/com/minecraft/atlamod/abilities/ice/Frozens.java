package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything currently sealed in a block of ice by {@link Freeze}.
 *
 * A victim in here is held completely still AND cannot be hurt by anything, and the
 * pairing is not two separate ideas — it is one. Two blocks of ice around something
 * puts a solid block where its eyes are, and vanilla suffocates anything in that
 * position; the immunity is what makes being encased survivable rather than a slow
 * execution. Take either half away and the ability stops working.
 *
 * That also makes Freeze a genuinely double-edged thing to cast, which is the point:
 * it takes a target out of the fight for ten seconds, and it protects them for ten
 * seconds. Freezing something your allies are busy killing is a mistake.
 */
public final class Frozens {

    /** How thick the shell is: the block at the feet, and the one at the head. */
    private static final int HEIGHT = 2;

    private static final List<Frozen> ACTIVE = new ArrayList<>();

    private Frozens() {
    }

    private static final class Frozen {
        final ServerLevel level;
        final UUID victimId;
        final List<BlockPos> shell;
        int ticksLeft;

        Frozen(ServerLevel level, UUID victimId, List<BlockPos> shell, int ticksLeft) {
            this.level = level;
            this.victimId = victimId;
            this.shell = shell;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Whether this thing is currently sealed in ice, and so untouchable. */
    public static boolean isFrozen(LivingEntity entity) {
        for (Frozen frozen : ACTIVE) {
            if (frozen.victimId.equals(entity.getUUID())) return true;
        }
        return false;
    }

    /**
     * Seals something in ice for a while.
     *
     * A second casting on the same victim is refused rather than stacking or
     * restarting — otherwise a bender could hold something frozen, and therefore
     * safe, indefinitely.
     */
    public static boolean freeze(ServerLevel level, LivingEntity victim, int ticks) {
        if (isFrozen(victim)) return false;

        List<BlockPos> shell = new ArrayList<>();
        BlockPos feet = victim.blockPosition();

        for (int dy = 0; dy < HEIGHT; dy++) {
            BlockPos at = feet.above(dy);
            if (IceWorks.freeze(level, at, Blocks.PACKED_ICE.defaultBlockState(), ticks)) {
                shell.add(at);
            }
        }

        // Held still by the same effect Lightning stun uses. A victim who could walk
        // out of the ice would make the whole ability pointless, and Stunned already
        // solves this for both mobs and players.
        victim.addEffect(new MobEffectInstance(ModEffects.STUNNED, ticks, 0, false, true, true));

        ACTIVE.add(new Frozen(level, victim.getUUID(), shell, ticks));

        Ice.form(level, victim.position(), 1.0F, 0.8F);
        Ice.frost(level, victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0), 40, 0.5);
        return true;
    }

    /** Counts every shell down and breaks the ones whose time is up. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        // A SNAPSHOT, because thawing touches entities and anything that touches
        // entities in this codebase can end up calling back in through a death
        // handler. See Rides for the crash this rule was written after.
        for (Frozen frozen : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(frozen)) continue;

            if (frozen.ticksLeft-- > 0) continue;

            thaw(frozen);
            ACTIVE.remove(frozen);
        }
    }

    private static void thaw(Frozen frozen) {
        // The shell is melted explicitly rather than left to its own timer, so the
        // ice and the immunity always end together. A victim standing inside ice they
        // could be hurt through is the one state this must never leave anyone in.
        IceWorks.meltNow(frozen.level, frozen.shell);

        if (!frozen.shell.isEmpty()) {
            Ice.shatter(frozen.level, Vec3.atCenterOf(frozen.shell.get(0)), 30, 0.5);
            Ice.crack(frozen.level, Vec3.atCenterOf(frozen.shell.get(0)), 1.0F, 1.1F);
        }
    }

    /** Called on death, logout and dimension change. */
    public static void forgetEntity(LivingEntity entity) {
        for (Frozen frozen : List.copyOf(ACTIVE)) {
            if (!frozen.victimId.equals(entity.getUUID())) continue;

            thaw(frozen);
            ACTIVE.remove(frozen);
        }
    }

    /**
     * Breaks every shell in a level that is going away.
     *
     * Melted rather than dropped, so a block of ice cannot be made permanent by
     * leaving the dimension while somebody is inside it.
     */
    public static void forgetLevel(ServerLevel level) {
        for (Frozen frozen : List.copyOf(ACTIVE)) {
            if (frozen.level != level) continue;

            thaw(frozen);
            ACTIVE.remove(frozen);
        }
    }
}
