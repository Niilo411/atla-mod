package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every metal shield standing in the world.
 *
 * Sound wall's heavier twin, and the difference is what it is MADE of: the sound wall
 * is particles that push things back, where this is REAL blocks. That makes it
 * genuinely solid — it collides the way any wall does, with no pushing logic at all —
 * and it is why it has to be built out of the unbreakable metal block and taken back
 * afterwards.
 *
 * Being real blocks also gives it the throw. There is a wall there to send forward,
 * so the left click picks it up and hurls it.
 */
public final class MetalShields {

    /** How far in front of the bender the shield hangs. */
    private static final double DISTANCE = 2.0;

    /** Half the shield's width, in blocks — three columns across. */
    private static final int HALF_WIDTH = 1;

    /** How far the thrown shield starts out and how far it travels. */
    private static final int THROW_FROM = 2;
    private static final int THROW_TO = 14;

    /** How tall it stands, from the bender's feet. */
    private static final int HEIGHT = 3;

    /** How long the blocks are lent for. Re-lent every time the shield moves. */
    private static final int LEND_TICKS = 40;

    /** What the thrown shield hits for. */
    private static final float THROW_DAMAGE = 4.0F;

    /** Two seconds of Stunned on whatever it hits. */
    private static final int THROW_STUN = 40;

    /** How wide the throw's damage sweep is, matching the wall's own footprint. */
    private static final double THROW_HALF_WIDTH = 2.0;

    private static final List<Shield> ACTIVE = new ArrayList<>();

    private MetalShields() {
    }

    private static final class Shield {
        final ServerLevel level;
        final UUID ownerId;

        /** Where the plates are standing right now, so they can be taken back. */
        final List<BlockPos> plates = new ArrayList<>();

        Shield(ServerLevel level, UUID ownerId) {
            this.level = level;
            this.ownerId = ownerId;
        }
    }

    public static boolean has(ServerPlayer player) {
        for (Shield shield : ACTIVE) {
            if (shield.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    public static void raise(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player)) return;

        ACTIVE.add(new Shield(level, player.getUUID()));
        Metal.scrape(level, player.position(), 1.0F, 0.9F);
    }

    /** Takes the shield down and gives the ground back. */
    public static void drop(ServerPlayer player) {
        for (Shield shield : List.copyOf(ACTIVE)) {
            if (!shield.ownerId.equals(player.getUUID())) continue;

            clear(shield);
            ACTIVE.remove(shield);
            Metal.scrape(shield.level, player.position(), 0.8F, 1.4F);
        }
    }

    /**
     * Hurls the shield forward, which is what the left click does.
     *
     * The thrown shield is a real travelling WALL, built out of the same EarthGrabs
     * wave Stone walls uses — the plates come down and the wave goes out. The one
     * difference is that this one is launched UNGROUNDED: it hangs on the line it was
     * thrown along rather than finding the surface, so a shield aimed upward really
     * does fly, where an earth wave always rides the terrain.
     */
    public static void hurl(ServerPlayer player) {
        for (Shield shield : List.copyOf(ACTIVE)) {
            if (!shield.ownerId.equals(player.getUUID())) continue;

            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            clear(shield);
            ACTIVE.remove(shield);

            fly(shield.level, player, data);
            return;
        }
    }

    /**
     * Sends the thrown shield out as a real wall, and hits what it is about to cross.
     *
     * UNGROUNDED, unlike every earth wave: it follows the full look vector, pitch
     * included, so it can be thrown up as readily as along the floor.
     *
     * The damage lands ONCE, up front, on everything in the corridor the wall is about
     * to pass through — the same call Stone walls makes. Hitting per tick as it
     * travelled would multiply four hearts by however long the throw took.
     */
    private static void fly(ServerLevel level, ServerPlayer owner, BendingData data) {
        Vec3 look = owner.getLookAngle();
        Vec3 origin = owner.getEyePosition();

        com.minecraft.atlamod.abilities.earth.EarthGrabs.launch(
                owner, origin, look, MetalWorks.metal(),
                THROW_FROM, THROW_TO, HALF_WIDTH, false);

        strike(level, owner, data, origin, look);

        Metal.clang(level, owner.position(), 1.4F, 0.8F);
    }

    /** Everything in the thrown wall's path takes the blow, once. */
    private static void strike(ServerLevel level, ServerPlayer owner, BendingData data,
                               Vec3 origin, Vec3 look) {
        float damage = Metal.damage(data, THROW_DAMAGE);

        AABB search = new AABB(origin, origin).inflate(THROW_TO);

        for (Entity caught : level.getEntities(owner, search)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 offset = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0)
                    .subtract(origin);

            double along = offset.dot(look);
            if (along < THROW_FROM || along > THROW_TO) continue;

            // Distance from the aim LINE, so a wall thrown at a pitch catches what it
            // really passes through rather than what happens to be level with it.
            if (offset.subtract(look.scale(along)).length() > THROW_HALF_WIDTH) continue;

            living.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
            living.addEffect(new MobEffectInstance(
                    ModEffects.STUNNED, THROW_STUN, 0, false, true, true));

            living.setDeltaMovement(look.x * 0.6, 0.3, look.z * 0.6);
            living.hurtMarked = true;
        }
    }

    /** Iterates a SNAPSHOT, like every other manager that touches entities. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Shield shield : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(shield)) continue;

            if (!advance(shield, server)) {
                clear(shield);
                ACTIVE.remove(shield);
            }
        }
    }

    private static boolean advance(Shield shield, MinecraftServer server) {
        ServerPlayer owner = server.getPlayerList().getPlayer(shield.ownerId);
        if (owner == null || owner.level() != shield.level || !owner.isAlive()) return false;

        // Follows the crosshair, flattened to the horizontal — looking up should aim
        // the shield, not tip it over the bender's head.
        Vec3 look = owner.getLookAngle();
        Vec3 facing = new Vec3(look.x, 0.0, look.z);
        if (facing.lengthSqr() < 1.0E-4) facing = new Vec3(0.0, 0.0, 1.0);
        facing = facing.normalize();

        Vec3 across = new Vec3(-facing.z, 0.0, facing.x);
        Vec3 centre = owner.position().add(facing.scale(DISTANCE));

        // Rebuilt from scratch each tick: the plates are taken back and laid again
        // wherever the shield now is. Simpler than working out which blocks moved, and
        // it means the ground is always given back correctly however fast it swings.
        clear(shield);

        for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w++) {
            for (int h = 0; h < HEIGHT; h++) {
                BlockPos plate = BlockPos.containing(
                        centre.add(across.scale(w)).add(0.0, h, 0.0));

                if (MetalWorks.lay(shield.level, plate, LEND_TICKS)) {
                    shield.plates.add(plate);
                }
            }
        }

        return true;
    }

    /** Gives back every block this shield is currently borrowing. */
    private static void clear(Shield shield) {
        if (shield.plates.isEmpty()) return;

        MetalWorks.restoreNow(shield.level, List.copyOf(shield.plates));
        shield.plates.clear();
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        for (Shield shield : List.copyOf(ACTIVE)) {
            if (!shield.ownerId.equals(player.getUUID())) continue;

            clear(shield);
            ACTIVE.remove(shield);
        }
    }

    public static void forgetLevel(ServerLevel level) {
        for (Shield shield : List.copyOf(ACTIVE)) {
            if (shield.level != level) continue;

            clear(shield);
            ACTIVE.remove(shield);
        }
    }
}
