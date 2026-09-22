package com.minecraft.atlamod.abilities.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every meteor currently in the world, through both of its lives: held hovering
 * above its bender, and thrown.
 *
 * Built out of the same "summoned, not borrowed" reasoning {@link
 * com.minecraft.atlamod.abilities.ice.IceBombs} documents for itself — the meteor is
 * conjured whole rather than assembled from real terrain, so it is destroyed rather
 * than given back, and every guarantee {@link com.minecraft.atlamod.abilities.HeldBlocks}
 * offers would work against it. The one real difference from an ice bomb is scale: a
 * meteor is 64 blocks, drawn as a rigid formation around one shared centre point
 * rather than a single display entity.
 */
public final class MeteorBlocks {

    /** The cube's side, matching the design after its "16x16x16" was cut down to size. */
    private static final int SIDE = 4;

    /** How far above the bender it hovers while held. */
    private static final double HOVER_HEIGHT = 10.0;

    /**
     * How far the throw travels before it gives up and goes off anyway. Long
     * deliberately — a meteor is meant to be thrown across the map, not just at
     * whatever happens to be a few blocks ahead.
     */
    private static final double THROW_DISTANCE = 200.0;

    private static final double THROW_SPEED = 1.6;

    private static final float DAMAGE = 16.0F;

    /**
     * Explosion power, sized to the user's own comparison: four Ghast fireballs
     * (vanilla power 1.0 apiece) plus four primed TNT (vanilla power 4.0 apiece) —
     * 4*1.0 + 4*4.0 = 20.0. Bigger than even Combustion nuke's individual blasts
     * (power 12), which is the point: this is the single biggest bang in the mod,
     * and it earns that with a fifteen second charge and a thirty second cooldown.
     */
    private static final float EXPLOSION_POWER = 20.0F;

    private static final BlockState[] PALETTE = {
            Blocks.STONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            Blocks.GRANITE.defaultBlockState(),
            Blocks.DIORITE.defaultBlockState(),
    };

    private static final List<Meteor> ACTIVE = new ArrayList<>();

    private MeteorBlocks() {
    }

    private enum Phase { HELD, THROWN }

    private static final class Meteor {
        final ServerLevel level;
        final UUID ownerId;
        final List<FallingBlockEntity> blocks;
        final List<Vec3> offsets;

        Phase phase = Phase.HELD;
        Vec3 centre;
        Vec3 heading = Vec3.ZERO;
        double travelled;

        Meteor(ServerLevel level, UUID ownerId, List<FallingBlockEntity> blocks,
               List<Vec3> offsets, Vec3 centre) {
            this.level = level;
            this.ownerId = ownerId;
            this.blocks = blocks;
            this.offsets = offsets;
            this.centre = centre;
        }
    }

    public static boolean has(ServerPlayer player) {
        for (Meteor meteor : ACTIVE) {
            if (meteor.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    /** Assembles the formation above the bender. */
    public static void summon(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player)) return;

        Vec3 centre = player.position().add(0.0, HOVER_HEIGHT, 0.0);
        List<FallingBlockEntity> blocks = new ArrayList<>();
        List<Vec3> offsets = new ArrayList<>();

        double half = (SIDE - 1) / 2.0;
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                for (int z = 0; z < SIDE; z++) {
                    Vec3 offset = new Vec3(x - half, y - half, z - half);
                    BlockState state = PALETTE[level.random.nextInt(PALETTE.length)];

                    Vec3 at = centre.add(offset);
                    BlockPos spawnAt = BlockPos.containing(at);

                    // FallingBlockEntity.fall CLEARS the block it spawns into — the
                    // meteor is conjured out of nothing, so whatever was floating up
                    // there has to be put straight back, the same care IceBombs takes.
                    BlockState occupied = level.getBlockState(spawnAt);
                    FallingBlockEntity display = FallingBlockEntity.fall(level, spawnAt, state);
                    if (!occupied.isAir()) {
                        level.setBlock(spawnAt, occupied,
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }

                    display.setNoGravity(true);
                    display.time = 0;
                    display.setDeltaMovement(Vec3.ZERO);
                    display.setPos(at.x, at.y, at.z);

                    blocks.add(display);
                    offsets.add(offset);
                }
            }
        }

        ACTIVE.add(new Meteor(level, player.getUUID(), blocks, offsets, centre));
        Gravity.gather(level, centre, 40, 2.0);
        Gravity.warp(level, centre, 1.3F);
    }

    /** Throws the bender's meteor down their crosshair. */
    public static void throwIt(ServerPlayer player) {
        for (Meteor meteor : ACTIVE) {
            if (!meteor.ownerId.equals(player.getUUID())) continue;
            if (meteor.phase != Phase.HELD) continue;

            meteor.phase = Phase.THROWN;
            meteor.heading = player.getLookAngle().normalize();
            meteor.travelled = 0.0;

            Gravity.snap(meteor.level, meteor.centre, 1.4F);
            return;
        }
    }

    /** Iterates a SNAPSHOT: detonating can kill things, which can reach back here. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Meteor meteor : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(meteor)) continue;

            if (!advance(meteor, server)) {
                ACTIVE.remove(meteor);
            }
        }
    }

    private static boolean advance(Meteor meteor, MinecraftServer server) {
        ServerPlayer owner = server.getPlayerList().getPlayer(meteor.ownerId);

        switch (meteor.phase) {
            case HELD -> {
                // Held dies with its bender's presence, exactly as a carried Ice Bomb
                // does — once it is thrown it is a hazard of its own and outlives them.
                if (owner == null || owner.level() != meteor.level || !owner.isAlive()) {
                    discard(meteor);
                    return false;
                }
                meteor.centre = owner.position().add(0.0, HOVER_HEIGHT, 0.0);
            }
            case THROWN -> {
                Vec3 next = meteor.centre.add(meteor.heading.scale(THROW_SPEED));
                meteor.travelled += THROW_SPEED;

                boolean blocked = meteor.level.getBlockState(BlockPos.containing(next)).isSolid();
                boolean hitSomething = !meteor.level.getEntities((Entity) null,
                        new AABB(next, next).inflate(1.5),
                        e -> e instanceof LivingEntity living && living.isAlive()
                                && !e.getUUID().equals(meteor.ownerId)).isEmpty();

                if (blocked || hitSomething || meteor.travelled >= THROW_DISTANCE) {
                    detonate(meteor, owner);
                    return false;
                }
                meteor.centre = next;
            }
        }

        park(meteor);
        return true;
    }

    /**
     * Holds every block of the formation at its offset from the shared centre.
     *
     * hasImpulse every tick, the same load-bearing line every FallingBlockEntity
     * carry in this mod needs — FALLING_BLOCK syncs once a second otherwise, which
     * reads as severe lag for something moved by hand every tick.
     */
    private static void park(Meteor meteor) {
        for (int i = 0; i < meteor.blocks.size(); i++) {
            FallingBlockEntity block = meteor.blocks.get(i);
            if (!block.isAlive()) continue;

            Vec3 at = meteor.centre.add(meteor.offsets.get(i));
            block.setNoGravity(true);
            block.setDeltaMovement(Vec3.ZERO);
            block.time = 0;
            block.setPos(at.x, at.y, at.z);
            block.hasImpulse = true;
        }
    }

    private static void detonate(Meteor meteor, ServerPlayer owner) {
        discard(meteor);

        Gravity.burst(meteor.level, meteor.centre, 150, 2.5);

        if (owner != null) {
            meteor.level.explode(owner, meteor.centre.x, meteor.centre.y, meteor.centre.z,
                    EXPLOSION_POWER, net.minecraft.world.level.Level.ExplosionInteraction.TNT);

            AABB blast = new AABB(meteor.centre, meteor.centre).inflate(4.0);
            for (Entity caught : meteor.level.getEntities((Entity) null, blast, e -> true)) {
                if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;
                if (living.position().distanceToSqr(meteor.centre) > 16.0) continue;

                living.hurt(owner.damageSources().indirectMagic(owner, owner), DAMAGE);
            }
        }
    }

    private static void discard(Meteor meteor) {
        for (FallingBlockEntity block : meteor.blocks) {
            if (block.isAlive()) block.discard();
        }
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        for (Meteor meteor : List.copyOf(ACTIVE)) {
            if (!meteor.ownerId.equals(player.getUUID())) continue;
            // Only a HELD meteor goes with them — a thrown one is already a hazard
            // in flight and finishes on its own, the same call IceBombs makes.
            if (meteor.phase != Phase.HELD) continue;

            discard(meteor);
            ACTIVE.remove(meteor);
        }
    }

    public static void forgetLevel(ServerLevel level) {
        for (Meteor meteor : List.copyOf(ACTIVE)) {
            if (meteor.level != level) continue;
            discard(meteor);
            ACTIVE.remove(meteor);
        }
    }
}
