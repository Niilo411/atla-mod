package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Lavabending's shared parts.
 *
 * The mod's SEVENTH sub-element, and the second to come out of earthbending. Like
 * combustion and blood it asks for ALL FOUR paths of its parent rather than the two
 * the first four scrolls want — lava is the end of the earth road, not a branch off it.
 *
 * There is deliberately NO element-wide wind-up here. Lightning has one and combustion
 * has one because the design gave every ability in those elements a charge; the lava
 * design gives a charge time to exactly two of its eight, so the other six go off on
 * the press like ordinary casts.
 *
 * What lavabending has instead of a wind-up is a rule about the world: every ability
 * except Lava throw places OUR lava, which never flows and is always taken back. Lava
 * throw is the one that leaves real, permanent, flowing lava behind — because the
 * design says "permanent" in so many words, and that is the whole point of it.
 */
public final class Lava {

    /**
     * Damage a body inside lava takes, and how often, matching vanilla's own figures.
     *
     * Vanilla's ten-tick invulnerability window is what actually spaces this out: the
     * block calls scorch() every tick for anything inside it, and all but every tenth
     * of those is discarded. Deliberately NOT pierced — lava is a hazard to stand in,
     * not an ability aimed at somebody, and a hazard that ignored i-frames would kill a
     * player in under a second.
     */
    public static final float CONTACT_DAMAGE = 4.0F;

    /** How long lava leaves something alight, in ticks. Vanilla's fifteen seconds. */
    public static final int BURN_TICKS = 300;

    private Lava() {
    }

    /** The block every temporary lava ability lays. Never flows, always taken back. */
    public static BlockState block() {
        return Atlamod.BENDING_LAVA.get().defaultBlockState();
    }

    /**
     * Burns whatever is standing in lava — ours or, through the abilities, whatever the
     * lava is being thrown at.
     *
     * Uses vanilla's own LAVA damage source rather than a bending one, and that is
     * load-bearing twice over: it makes our lava indistinguishable from the real thing
     * to everything else in the game, and it means {@link LavaResistance} can be one
     * check against one damage type instead of a list of ability names.
     */
    public static void scorch(Entity entity) {
        if (entity.fireImmune()) return;
        if (isProtected(entity)) return;

        entity.setRemainingFireTicks(BURN_TICKS);
        entity.hurt(entity.damageSources().lava(), CONTACT_DAMAGE);
    }

    /**
     * Whether this thing is wearing Lava resistance.
     *
     * Only players can equip a passive, so this is a player question — but it is asked
     * of every Entity, because the lava block does not care what walked into it.
     */
    public static boolean isProtected(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;

        return player.getData(ModAttachments.BENDING_DATA)
                .hasPassiveEquipped(LavaResistance.KEY);
    }

    /**
     * A lavabending ability's damage.
     *
     * Lavabending has no equivalent of Lightning Strength or Sound boosting, so this is
     * a pass-through. It exists so every lava ability already routes through one place
     * if a bonus is ever added.
     */
    public static float damage(BendingData data, float base) {
        return base;
    }

    /** Molten spatter: what lava looks like when it is being moved about. */
    public static void spatter(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, count, spread, spread, spread, 0.0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z,
                Math.max(1, count / 2), spread * 0.6, spread * 0.6, spread * 0.6, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z,
                Math.max(1, count / 3), spread, spread * 0.5, spread, 0.01);
    }

    /** The hiss of something molten arriving. */
    public static void hiss(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.LAVA_POP, SoundSource.PLAYERS, volume, pitch);
    }

    /** The deeper roar the bigger abilities open with. */
    public static void roar(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, volume, pitch);
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, volume, pitch * 0.8F);
    }

    /**
     * The first open space with solid ground under it near {@code target}, or null.
     *
     * Every lava ability that lays lava on the ground uses this, so a wall, a river or
     * a wave all ride over the terrain in the same way instead of each having its own
     * slightly different idea of where the floor is. Copied in spirit from Tsunami's
     * findFooting, which is the same question asked of water.
     */
    @Nullable
    public static BlockPos footing(ServerLevel level, BlockPos target, int upScan, int downScan) {
        for (int dy = upScan; dy >= -downScan; dy--) {
            BlockPos pos = target.above(dy);
            BlockState here = level.getBlockState(pos);

            boolean open = here.isAir() || here.canBeReplaced();
            if (open && level.getBlockState(pos.below()).isSolid()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * The bender's facing, flattened onto the horizontal plane.
     *
     * Shared because four lava abilities lay something along the ground in front of the
     * bender, and all four have to behave the same way when somebody casts while
     * looking straight up — which is to fall back to the way the body is turned rather
     * than producing a zero-length direction.
     */
    public static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);

        if (flat.lengthSqr() < 1.0E-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        return flat.normalize();
    }
}
