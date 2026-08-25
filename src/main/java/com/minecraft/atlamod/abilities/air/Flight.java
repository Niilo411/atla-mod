package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.abilities.PassiveAbility;
import com.minecraft.atlamod.abilities.fire.FireRocket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Air. Passive. The bender can simply fly, at half the pace a creative
 * one would.
 *
 * Free to run, like every other passive: being slotted IS the ability, and there is
 * no moment of use to hang a cost on. What it buys is permanent, which is what makes
 * it a masterclass unlock rather than a cheap one.
 *
 * Vanilla's own flight is used rather than anything hand-rolled, which is why it
 * feels right — the client is genuinely in flight mode, so there is nothing for the
 * server to correct and the usual double-tap of space still toggles it. All this does
 * is grant the permission and halve the speed.
 *
 * Flight flags are PERSISTED in player NBT, so see stopFlying() and the note on
 * BendingData#isPassiveFlightGranted for why revoking is handled as carefully as it is.
 */
public class Flight implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "flight";

    /** Vanilla creative flight is 0.05, so this is exactly half. */
    private static final float FLIGHT_SPEED = 0.025F;
    private static final float DEFAULT_FLY_SPEED = 0.05F;

    /**
     * How far above sea level a bender may climb. Measured from the level's own sea
     * level rather than a fixed Y, so the ceiling means the same thing in a dimension
     * that sits at a different height — the Nether's is 32, not 63.
     */
    private static final int CEILING_ABOVE_SEA_LEVEL = 120;

    /** How often the ceiling says so, in ticks. Every tick would be a stutter of text. */
    private static final int CEILING_MESSAGE_INTERVAL = 20;

    @Override
    public String getName() {
        return "Flight";
    }

    @Override
    public String getDescription() {
        return "Fly freely, at half a creative flier's speed";
    }

    /**
     * Called every tick from ServerEvents, whether or not the passive is equipped —
     * taking flight AWAY is as much this method's job as granting it.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        // A creative or spectating player's flight is not ours to touch, in either
        // direction. Revoking it would be a genuinely destructive bug.
        if (player.isCreative() || player.isSpectator()) return;

        // Fire Rocket owns the flight flags while it is channelling, and sets its own
        // speed. Two things writing them in the same tick would fight.
        if (data.getActiveChanneledAbility().equals(FireRocket.KEY)) return;

        if (data.hasPassiveEquipped(KEY)) {
            grantFlying(player, data);
            holdCeiling(player);
        } else if (data.isPassiveFlightGranted()) {
            stopFlying(player, data);
        }
    }

    /**
     * Stops a flier climbing past the ceiling.
     *
     * Only UPWARD motion is taken away, and only while actually flying. Nothing pins
     * the player, nothing moves them, and horizontal flight at the ceiling is left
     * completely alone — pinning a position server-side is what made Fire Rocket's old
     * height cap rubber-band, because the client owns the player's movement and simply
     * disagrees. Cancelling the climb instead reads as a ceiling to push against,
     * which is what it is.
     *
     * A bender already above the line (carried there some other way) is not shoved
     * down either. They just cannot go any higher.
     */
    private static void holdCeiling(ServerPlayer player) {
        if (!player.getAbilities().flying) return;

        double ceiling = player.level().getSeaLevel() + CEILING_ABOVE_SEA_LEVEL;
        if (player.getY() < ceiling) return;

        Vec3 motion = player.getDeltaMovement();
        if (motion.y <= 0.0) return;

        player.setDeltaMovement(motion.x, 0.0, motion.z);
        // Players ignore server-side velocity unless it is explicitly pushed to them.
        player.hurtMarked = true;

        if (player.tickCount % CEILING_MESSAGE_INTERVAL == 0) {
            player.displayClientMessage(
                    Component.literal("§bThe air is too thin to climb any higher!"), true);
        }
    }

    /**
     * Grants the permission to fly — but does NOT force the player into the air.
     *
     * The difference from Fire Rocket matters: that ability owns its flight and
     * re-asserts it every tick so only the keybind can end it. This is creative
     * flight, and a creative flier is free to land and take off again with a
     * double-tap of space. Forcing the flag would take that away.
     */
    private static void grantFlying(ServerPlayer player, BendingData data) {
        if (data.isPassiveFlightGranted()
                && player.getAbilities().mayfly
                && player.getAbilities().getFlyingSpeed() == FLIGHT_SPEED) {
            return; // already set up; don't spam ability packets
        }

        player.getAbilities().mayfly = true;
        player.getAbilities().setFlyingSpeed(FLIGHT_SPEED);
        player.onUpdateAbilities();

        data.setPassiveFlightGranted(true);
    }

    /**
     * Takes flight back, and is the whole reason BendingData remembers having granted
     * it. Both flags are written to player NBT by Abilities#addSaveData, so a bender
     * who unequipped the passive in mid-air would otherwise keep creative flight for
     * good.
     */
    public static void stopFlying(ServerPlayer player, BendingData data) {
        data.setPassiveFlightGranted(false);

        if (player.isCreative() || player.isSpectator()) return;

        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);
        player.onUpdateAbilities();

        player.setData(ModAttachments.BENDING_DATA, data);
    }
}
