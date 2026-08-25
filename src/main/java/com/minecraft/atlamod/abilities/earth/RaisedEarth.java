package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilitySupport;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Earth pulled up out of the ground and held there — the shape Earth wall and Earth
 * pillar both have.
 *
 * HELD, one block of height per second, capping itself at {@link EarthWalls#MAX_LAYERS}.
 * What goes up then stands for half a minute and sinks back. Held rather than tapped
 * because the height is the decision being made, and what a bender wants out of the
 * ground is a different thing at one block than at seven.
 *
 * Subclasses supply only three things: what it is called, what it costs, and WHICH
 * COLUMNS to raise. Everything else — the chi, the cap, the lifecycle, the fact that
 * it outlives its own channel — is the same for all of them and lives here.
 */
public abstract class RaisedEarth implements ChanneledAbility {

    /** How far each column hunts for its own ground, so a raise follows a slope. */
    protected static final int UP_SCAN = 2;
    protected static final int DOWN_SCAN = 3;

    /** Chi taken once, up front, however tall it ends up. */
    protected abstract int chiCost();

    /** XP granted once, on raising it at all. */
    protected abstract int xpReward();

    /**
     * The ground each column will stand on.
     *
     * Every column finds its own footing rather than all of them sharing the bender's
     * height, so earth raised across a slope follows the slope instead of floating over
     * the low end and burying itself in the high one. Columns with no ground at all are
     * simply left out, which is what makes a wall over a chasm shorter rather than
     * broken.
     */
    protected abstract List<BlockPos> surfaces(ServerPlayer player);

    /** Channels are not billed through the cast path; see onStart. */
    @Override
    public int getChiCost() {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    /**
     * Nothing per second. Raising earth costs a flat price whether it ends up one
     * block tall or seven: what is being paid for is pulling it up at all, not the
     * time spent holding the key.
     */
    @Override
    public int getChiPerSecond(BendingData data) {
        return 0;
    }

    /** The flat cost, as a gate. It is actually spent in onStart. */
    @Override
    public int getMinimumChiToStart(BendingData data) {
        return chiCost();
    }

    @Override
    public double getXpPerSecond() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 20; // 1 second, applied when the channel ends
    }

    /** Refused for free when there is no ground to pull anything out of. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (!surfaces(player).isEmpty()) return true;

        player.displayClientMessage(
                Component.literal("§6There is no ground here to raise!"), true);
        return false;
    }

    /**
     * Ends the channel the moment it stops growing, which is how the height caps
     * itself without a duration that has to be kept in step with it.
     */
    @Override
    public boolean canContinue(ServerPlayer player, BendingData data) {
        return EarthWalls.isGrowing(player);
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        List<BlockPos> surfaces = surfaces(player);
        List<BlockState> materials = new ArrayList<>(surfaces.size());
        for (BlockPos surface : surfaces) {
            materials.add(EarthWorks.materialUnder(level, surface));
        }

        if (!EarthWalls.begin(player, surfaces, materials)) return;

        // Charged here rather than by the dispatcher: a channel's chi is a per-second
        // rate, and this one is a single price for the whole thing.
        data.consumeChi(chiCost());
        AbilitySupport.grantXp(data, xpReward());

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.3F, 0.6F);
    }

    /** Growth is driven by EarthWalls, which has to keep running after this ends. */
    @Override
    public void onTick(ServerPlayer player, BendingData data) {
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        EarthWalls.stopGrowing(player);
    }

    /**
     * Where the bender is facing, flattened onto the ground.
     *
     * Raised earth stands on the ground regardless of whether the bender is looking at
     * the sky or at their feet, and looking straight up or down falls back to the way
     * the body is turned rather than producing no direction at all.
     */
    protected static Vec3 facing(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);

        if (flat.lengthSqr() < 1.0E-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }

        return flat.normalize();
    }
}
