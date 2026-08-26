package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Left / Metal. Everything in the corridor three to ten blocks ahead is pinned where
 * it stands, and then two walls of metal slam together through it.
 *
 * The pin is what makes it land. A crush that could be walked out of would be a very
 * expensive way to make a noise, so the victims are Stunned first and the walls close
 * a moment later — that gap is deliberate and is the only warning anyone gets.
 *
 * It starts three blocks out, not at the bender's feet: the walls close through the
 * whole corridor, and a bender standing inside their own crush is not the intent.
 *
 * Its costs were NOT in the design, which gives this ability no numbers at all. They
 * are set in line with its siblings.
 */
public class Crush implements Ability {

    /** How far ahead the corridor starts and ends, in blocks. */
    private static final double NEAR = 3.0;
    private static final double FAR = 10.0;

    /** How wide the corridor is, either side of the aim line. */
    private static final double HALF_WIDTH = 2.5;

    /** How long the victims are held before the walls close. */
    private static final int PIN_TICKS = 30;

    /** What the closing walls hit for. INVENTED: the design gives no figure. */
    private static final float DAMAGE = 12.0F;

    /** How long the walls stand before the ground is given back. */
    private static final int WALL_TICKS = 60;

    /** INVENTED: no chi cost in the design. In line with its siblings. */
    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    /** INVENTED: no xp reward in the design. */
    @Override
    public int getXpReward() {
        return 15;
    }

    /** INVENTED: no cooldown in the design. */
    @Override
    public int getCooldownTicks() {
        return 300; // 15 seconds
    }

    @Override
    public String getName() {
        return "Crush";
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 look = player.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) heading = new Vec3(0.0, 0.0, 1.0);
        heading = heading.normalize();

        Vec3 across = new Vec3(-heading.z, 0.0, heading.x);

        // --- The pin ---
        List<LivingEntity> caught = inCorridor(player, level, heading, across);
        for (LivingEntity victim : caught) {
            victim.addEffect(new MobEffectInstance(
                    ModEffects.STUNNED, PIN_TICKS, 0, false, true, true));
        }

        Metal.scrape(level, player.position(), 1.2F, 0.8F);

        // --- The walls ---
        // Laid down the two long sides of the corridor and taken back afterwards, so
        // the crush is visible rather than being damage out of nowhere.
        Vec3 centre = player.position().add(heading.scale((NEAR + FAR) * 0.5));

        for (double along = NEAR; along <= FAR; along += 1.0) {
            for (int h = 0; h < 3; h++) {
                Vec3 spine = player.position().add(heading.scale(along)).add(0.0, h, 0.0);

                MetalWorks.lay(level, BlockPos.containing(spine.add(across.scale(HALF_WIDTH))), WALL_TICKS);
                MetalWorks.lay(level, BlockPos.containing(spine.add(across.scale(-HALF_WIDTH))), WALL_TICKS);
            }
        }

        // --- The blow ---
        // Dealt immediately rather than after the pin, because the walls closing IS
        // the crush and the stun is only what stops it being dodged. Waiting would
        // mean tracking a delayed hit for a moment nobody can escape anyway.
        float damage = Metal.damage(data, DAMAGE);
        for (LivingEntity victim : caught) {
            victim.hurt(player.damageSources().indirectMagic(player, player), damage);
            Metal.spark(level, victim.getEyePosition(), 20, 0.4);
        }

        Metal.clang(level, centre, 1.6F, 0.7F);
    }

    /** Everything standing in the corridor ahead. */
    private static List<LivingEntity> inCorridor(ServerPlayer player, ServerLevel level,
                                                 Vec3 heading, Vec3 across) {
        List<LivingEntity> found = new ArrayList<>();

        AABB search = new AABB(player.position(), player.position()).inflate(FAR);

        for (Entity candidate : level.getEntities(player, search)) {
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 offset = living.position().subtract(player.position());

            double along = offset.dot(heading);
            if (along < NEAR || along > FAR) continue;
            if (Math.abs(offset.dot(across)) > HALF_WIDTH) continue;
            if (Math.abs(offset.y) > 3.0) continue;

            found.add(living);
        }
        return found;
    }
}
