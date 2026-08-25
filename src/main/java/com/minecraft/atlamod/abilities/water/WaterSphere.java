package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Balanced / Water. Holds the water back in a sphere around the bender, so an ocean
 * can be walked through rather than swum.
 *
 * The pocket travels with them: water closes in behind and opens ahead, and letting
 * go fills the whole thing in. WaterSpheres does the bookkeeping, and the reason it
 * exists is that every block taken out has to be remembered — otherwise a bender
 * could drain a sea simply by walking across the bottom of it.
 *
 * It can be opened on dry land, where it simply finds no water to hold back and waits
 * until there is some. That is deliberate: a bender should be able to raise the sphere
 * on the shore and then walk in, rather than having to dive first and open it while
 * already drowning.
 */
public class WaterSphere implements ChanneledAbility {

    /** How far the water is held back. */
    private static final double RADIUS = 5.0;

    @Override
    public String getName() {
        return "Water Sphere";
    }

    /** Paid per second while channeling, not up front. */
    @Override
    public int getChiCost() {
        return 0;
    }

    /** Granted per second, not up front. */
    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 2;
    }

    @Override
    public double getXpPerSecond() {
        return 2;
    }

    /**
     * Deliberately NOT gated on the canteen, like the other water abilities that name
     * their own source: this one has the bender standing in the sea.
     */
    @Override
    public boolean requiresWater() {
        return false;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        WaterSpheres.update(player, RADIUS);

        if (!(player.level() instanceof ServerLevel level)) return;

        // The wall of held-back water, shown thinly so the pocket has an edge.
        if (player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    6, RADIUS * 0.7, RADIUS * 0.5, RADIUS * 0.7, 0.0);
        }
    }

    /** Letting go lets the sea back in. */
    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        WaterSpheres.collapse(player);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.PLAYERS, 1.0F, 0.8F);
    }
}
