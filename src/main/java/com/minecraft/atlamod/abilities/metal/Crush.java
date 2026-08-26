package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.earth.EarthGrabs;
import com.minecraft.atlamod.abilities.earth.EarthWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Left / Metal. Everything in the corridor three to ten blocks ahead is pinned where
 * it stands, and then two walls of GROUND roll in from either side and meet in the
 * middle.
 *
 * The walls are made of whatever the ground is — stone, dirt, sand, whatever the
 * corridor is standing on — and NOT of metal. A metalbender who could conjure two
 * walls of iron out of nothing would have a very different ability; what this does is
 * take hold of the ground, which is why it borrows earthbending's wave wholesale.
 *
 * That wave is {@link EarthGrabs}, the same moving body of slices Earth grab uses,
 * pointed sideways and launched twice — once from each side of the corridor, each
 * travelling inward. Everything they pass over is carried along in front of them, so
 * the crush arrives smoothly rather than as damage out of nowhere.
 *
 * The pin is what makes it land. A crush that could simply be walked out of would be
 * a very expensive way to make a noise, so the victims are Stunned as it starts and
 * the walls take a moment to close — that gap is the only warning anyone gets.
 *
 * Its costs were NOT in the design, which gives this ability no numbers at all.
 */
public class Crush implements Ability {

    /** How far ahead the corridor starts and ends, in blocks. */
    private static final double NEAR = 3.0;
    private static final double FAR = 10.0;

    /** How far out to either side the walls start. */
    private static final int HALF_WIDTH = 5;

    /** How long the victims are held. Long enough for the walls to arrive. */
    private static final int PIN_TICKS = 40;

    /** What the closing walls hit for. INVENTED: the design gives no figure. */
    private static final float DAMAGE = 12.0F;

    @Override
    public String getName() {
        return "Crush";
    }

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
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 look = player.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) heading = new Vec3(0.0, 0.0, 1.0);
        heading = heading.normalize();

        Vec3 across = new Vec3(-heading.z, 0.0, heading.x);

        // The middle of the corridor: where the two walls will meet.
        Vec3 centre = player.position().add(heading.scale((NEAR + FAR) * 0.5));

        // --- The pin ---
        List<LivingEntity> caught = inCorridor(player, level, heading, across);
        for (LivingEntity victim : caught) {
            victim.addEffect(new MobEffectInstance(
                    ModEffects.STUNNED, PIN_TICKS, 0, false, true, true));
        }

        // --- The walls ---
        // Whatever the ground under the middle of the corridor is, so the walls look
        // like the place they came out of rather than like every crush everywhere.
        BlockState material = materialUnder(level, centre);

        // Two waves along the SIDEWAYS axis, each starting out at the corridor's edge
        // and travelling in to the middle. EarthGrabs carries what it catches in its
        // own direction of travel, so the two of them drive everything together.
        EarthGrabs.launch(player, centre, across, material, HALF_WIDTH, 0);
        EarthGrabs.launch(player, centre, across.scale(-1.0), material, HALF_WIDTH, 0);

        // --- The blow ---
        // Dealt now rather than when the walls meet: the victims are pinned and the
        // walls are already on their way, so there is no moment between the two that
        // anyone can use. Tracking a delayed hit would buy nothing.
        float damage = Metal.damage(data, DAMAGE);
        for (LivingEntity victim : caught) {
            victim.hurt(player.damageSources().indirectMagic(player, player), damage);
        }

        Metal.scrape(level, player.position(), 1.2F, 0.8F);
    }

    /**
     * What the walls should be made of.
     *
     * EarthWorks.materialFor is earthbending's own answer to this and is reused whole:
     * it mirrors the surface so a wall out of a hillside looks like the hillside, swaps
     * anything that FALLS for dirt so the wall does not collapse the instant it goes
     * up, and falls back to dirt for anything that is not plain diggable ground.
     */
    private static BlockState materialUnder(ServerLevel level, Vec3 at) {
        BlockPos ground = EarthWorks.surfaceUnder(level, BlockPos.containing(at), 3, 4);
        if (ground == null) return net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();

        return EarthWorks.materialFor(level, ground);
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
