package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.PassiveAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Left / Lightning. Passive. The bender is quickened by their own element:
 * permanent Speed I, chi that comes back twice as fast, and every lightning ability
 * hitting half again as hard.
 *
 * Three separate things, in three separate places, because there is no one hook
 * that could do all of them:
 *  - the Speed is applied here, from the player tick;
 *  - the chi regen is doubled in ServerEvents' regen block, which is the only place
 *    that knows how much is being handed back;
 *  - the damage is applied by the abilities themselves through {@link Lightning#damage},
 *    since there is no damage-source signature that means "a lightning ability" the
 *    way IS_FIRE does for fire.
 */
public class LightningStrength implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "lightning strength";

    /** Speed I. */
    private static final int SPEED_LEVEL = 0;

    /**
     * How much faster chi comes back. Base regen is 1% of max per second, so a full
     * refill takes 100 seconds; doubling it makes that the 50 the design asks for.
     */
    public static final int CHI_REGEN_MULTIPLIER = 2;

    @Override
    public String getName() {
        return "Lightning Strength";
    }

    @Override
    public String getDescription() {
        return "Permanent Speed I, chi regenerates twice as fast, and lightning abilities deal 50% more damage";
    }

    /**
     * Keeps Speed I topped up while the passive is equipped.
     *
     * Speed is not counter-driven the way Regeneration is, so it can simply be
     * re-applied — but not every tick, since each addEffect is a packet. It is only
     * refreshed once the standing instance is nearly out.
     *
     * A stronger Speed the player got from somewhere else is left alone: the check
     * is on the amplifier, so drinking a Speed II potion is not quietly downgraded
     * to Speed I twenty times a second.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        boolean equipped = data.hasPassiveEquipped(KEY);

        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);

        if (equipped) {
            if (speed == null || (speed.getAmplifier() <= SPEED_LEVEL && speed.getDuration() < 40)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                        200, SPEED_LEVEL, false, true, true));
            }
            return;
        }

        // Unequipped: take back the Speed, but only the exact one this grants, so a
        // potion the player drank is not stripped along with it. Anything with time
        // left beyond what this passive ever gives was not ours either.
        if (speed != null && speed.getAmplifier() == SPEED_LEVEL && speed.getDuration() <= 200) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
    }
}
