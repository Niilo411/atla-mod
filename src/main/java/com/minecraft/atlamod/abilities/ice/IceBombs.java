package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every ice bomb in the world, through all three of its lives: carried on the
 * crosshair, thrown, and sitting there ticking.
 *
 * Not built on {@link com.minecraft.atlamod.abilities.HeldBlocks} even though the
 * carry looks identical, and the reason is what that class is FOR: it takes a real
 * block out of the world and is careful to put it back, because losing one would be
 * destroying terrain. An ice bomb is SUMMONED out of nothing and is meant to be
 * destroyed at the end, so every guarantee HeldBlocks offers would be working against
 * this ability rather than for it.
 */
public final class IceBombs {

    /** How far ahead of the bender the bomb floats while carried. */
    private static final double CARRY_DISTANCE = 3.0;

    /** How far the throw carries it, in blocks. "A few", as specced. */
    private static final double THROW_DISTANCE = 6.0;

    /** How fast it travels once thrown, in blocks per tick. */
    private static final double THROW_SPEED = 0.5;

    /**
     * How long it sits after landing before going off. Cut by three seconds.
     *
     * Five was long enough for anything with legs to simply walk out of a four block
     * blast and come back afterwards, which left the bomb doing nothing but marking a
     * square of ground as briefly unpleasant.
     */
    private static final int FUSE_TICKS = 40; // 2 seconds

    /** How wide the blast reaches. */
    private static final double BLAST_RADIUS = 4.0;

    /**
     * What the blast hits for.
     *
     * INVENTED: the design gives this ability no damage figure at all, only "a ton of
     * ice particles". A thing called a bomb that did nothing but sparkle seemed far
     * more likely to be an omission than a decision, so it hits — but this number and
     * the costs on IceBomb are the two things in the element that were not specced.
     */
    private static final float BLAST_DAMAGE = 8.0F;

    private static final List<Bomb> ACTIVE = new ArrayList<>();

    private IceBombs() {
    }

    private enum Phase { CARRIED, THROWN, FUSED }

    private static final class Bomb {
        final ServerLevel level;
        final UUID ownerId;
        final FallingBlockEntity display;

        Phase phase = Phase.CARRIED;
        Vec3 pos;
        Vec3 heading = Vec3.ZERO;
        double travelled;
        int fuse = FUSE_TICKS;

        Bomb(ServerLevel level, UUID ownerId, FallingBlockEntity display, Vec3 pos) {
            this.level = level;
            this.ownerId = ownerId;
            this.display = display;
            this.pos = pos;
        }
    }

    public static boolean has(ServerPlayer player) {
        for (Bomb bomb : ACTIVE) {
            if (bomb.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    /** Summons one onto the bender's crosshair. */
    public static void summon(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player)) return;

        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(CARRY_DISTANCE));

        net.minecraft.core.BlockPos spawnAt = net.minecraft.core.BlockPos.containing(at);

        // FallingBlockEntity.fall CLEARS the block at the position it spawns in.
        // HeldBlocks gets away with calling it because the block it names has just
        // been removed on purpose; an ice bomb is summoned out of NOTHING, so without
        // putting it straight back this would delete whatever happened to be floating
        // three blocks in front of the bender on every single cast.
        net.minecraft.world.level.block.state.BlockState occupied = level.getBlockState(spawnAt);

        FallingBlockEntity display = FallingBlockEntity.fall(
                level, spawnAt, Blocks.PACKED_ICE.defaultBlockState());

        if (!occupied.isAir()) {
            // UPDATE_CLIENTS, so putting it back does not set off a cascade of
            // neighbour updates for a block that never really went anywhere.
            level.setBlock(spawnAt, occupied, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }

        display.setNoGravity(true);
        display.time = 0;
        display.setDeltaMovement(Vec3.ZERO);

        ACTIVE.add(new Bomb(level, player.getUUID(), display, at));
        Ice.form(level, at, 0.7F, 1.2F);
    }

    /** Throws the bender's carried bomb down their crosshair. */
    public static void throwIt(ServerPlayer player) {
        for (Bomb bomb : ACTIVE) {
            if (!bomb.ownerId.equals(player.getUUID())) continue;
            if (bomb.phase != Phase.CARRIED) continue;

            bomb.phase = Phase.THROWN;
            bomb.heading = player.getLookAngle().normalize();
            bomb.travelled = 0.0;

            Ice.crack(bomb.level, bomb.pos, 0.7F, 1.3F);
            return;
        }
    }

    /**
     * Iterates a SNAPSHOT: a bomb kills things, a death fires the handler that calls
     * {@link #forgetPlayer}, and that removes from the very list being walked.
     */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Bomb bomb : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(bomb)) continue;

            if (!advance(bomb, server)) {
                ACTIVE.remove(bomb);
            }
        }
    }

    private static boolean advance(Bomb bomb, MinecraftServer server) {
        ServerPlayer owner = server.getPlayerList().getPlayer(bomb.ownerId);

        switch (bomb.phase) {
            case CARRIED -> {
                // A carried bomb dies with its bender's presence. A thrown one does
                // NOT — once it is out of their hands it is a hazard in a place, and
                // it should still go off if they log out or walk into a portal.
                if (owner == null || owner.level() != bomb.level || !owner.isAlive()) {
                    discard(bomb);
                    return false;
                }
                bomb.pos = owner.getEyePosition().add(owner.getLookAngle().scale(CARRY_DISTANCE));
            }
            case THROWN -> {
                Vec3 next = bomb.pos.add(bomb.heading.scale(THROW_SPEED));
                bomb.travelled += THROW_SPEED;

                boolean blocked = bomb.level.getBlockState(
                        net.minecraft.core.BlockPos.containing(next)).isSolid();

                if (blocked || bomb.travelled >= THROW_DISTANCE) {
                    bomb.phase = Phase.FUSED;
                } else {
                    bomb.pos = next;
                }
            }
            case FUSED -> {
                // Ticking. The frost thickens as the fuse runs down, so anyone nearby
                // can see it is about to go rather than only hearing it afterwards.
                if (bomb.fuse % 10 == 0) {
                    Ice.frost(bomb.level, bomb.pos, 6, 0.5);
                    Ice.crack(bomb.level, bomb.pos, 0.4F, 1.8F);
                }

                if (bomb.fuse-- <= 0) {
                    detonate(bomb, owner);
                    discard(bomb);
                    return false;
                }
            }
        }

        park(bomb);
        return true;
    }

    /**
     * Holds the display block where the bomb is.
     *
     * hasImpulse every tick is load-bearing: FALLING_BLOCK is registered with
     * updateInterval(20), so its position is broadcast ONCE A SECOND unless that flag
     * is set — a bomb moved by hand without it arrives on the client in one-second
     * teleports, which reads as severe lag while costing nothing on the server.
     */
    private static void park(Bomb bomb) {
        if (!bomb.display.isAlive()) return;

        bomb.display.setNoGravity(true);
        bomb.display.setDeltaMovement(Vec3.ZERO);
        // Its own timer would otherwise land it as a block or drop it as an item.
        bomb.display.time = 0;
        bomb.display.setPos(bomb.pos.x, bomb.pos.y - 0.5, bomb.pos.z);
        bomb.display.hasImpulse = true;
    }

    private static void detonate(Bomb bomb, ServerPlayer owner) {
        ServerLevel level = bomb.level;

        // The ton of ice particles the design asks for, in layers rather than one
        // call: a single batch of 200 lands as one shapeless puff, where three at
        // different spreads reads as a burst throwing shards outward.
        Ice.shatter(level, bomb.pos, 120, 0.5);
        Ice.shatter(level, bomb.pos, 80, 1.5);
        Ice.shatter(level, bomb.pos, 60, 3.0);
        Ice.crack(level, bomb.pos, 1.6F, 0.7F);

        if (owner == null) return; // Nothing to attribute the damage to.

        BendingData data = owner.getData(ModAttachments.BENDING_DATA);
        float damage = Ice.damage(data, BLAST_DAMAGE);

        AABB blast = new AABB(bomb.pos, bomb.pos).inflate(BLAST_RADIUS);

        // null, not the owner: this is a hazard going off in a place, not a spell
        // aimed at somebody. Standing next to your own bomb should hurt — the same
        // call Air spout and Lightning ball make.
        for (Entity caught : level.getEntities((Entity) null, blast, e -> true)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.position().distanceToSqr(bomb.pos) > BLAST_RADIUS * BLAST_RADIUS) continue;

            living.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
            Ice.chill(living, 100, 0);
        }
    }

    private static void discard(Bomb bomb) {
        if (bomb.display.isAlive()) bomb.display.discard();
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        for (Bomb bomb : List.copyOf(ACTIVE)) {
            if (!bomb.ownerId.equals(player.getUUID())) continue;
            // Only a CARRIED bomb goes with them; one already thrown is out of their
            // hands and finishes its fuse on its own.
            if (bomb.phase != Phase.CARRIED) continue;

            discard(bomb);
            ACTIVE.remove(bomb);
        }
    }

    public static void forgetLevel(ServerLevel level) {
        for (Bomb bomb : List.copyOf(ACTIVE)) {
            if (bomb.level != level) continue;

            discard(bomb);
            ACTIVE.remove(bomb);
        }
    }
}
