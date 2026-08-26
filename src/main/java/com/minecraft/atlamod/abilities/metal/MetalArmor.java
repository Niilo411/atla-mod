package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;

/**
 * Left / Metal. A suit of iron drawn out of the air for thirty seconds.
 *
 * Earth armor's lighter, faster cousin: a third of the chi, a thirtieth of the
 * cooldown, and a quarter of the duration. Where that one is a commitment, this is
 * something to throw up between fights.
 *
 * Like Earth armor it is a registered MobEffect carrying an ARMOR attribute modifier,
 * so the duration, the removal, the inventory timer and the cleanup on death all come
 * from vanilla rather than from bookkeeping here.
 *
 * The upgrade swaps which EFFECT is applied rather than raising an amplifier, and
 * that is arithmetic rather than taste: vanilla scales an ADD_VALUE modifier by
 * (amplifier + 1), so one registration can only produce a base and its double. Iron's
 * 15 and diamond's 20 are not in that relationship. Two effects give each its exact
 * figure -- see ModEffects.
 *
 * Both are removed when the other is applied, so re-casting after buying the upgrade
 * swaps the suit rather than wearing both at once.
 */
public class MetalArmor implements Ability {

    /** Key of the upgrade that raises the suit from iron to diamond. */
    public static final String DIAMOND_PLATING = "metal_armor_diamond_plating";

    /** Thirty seconds. */
    private static final int DURATION = 600;

    @Override
    public String getName() {
        return "Metal armor";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 100; // 5 seconds
    }

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(new AbilityUpgrade(
                DIAMOND_PLATING,
                "Diamond Plating",
                "The suit is as strong as diamond armor rather than iron",
                25));
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        boolean diamond = data.hasUpgrade(DIAMOND_PLATING);

        // The other one comes off first. Without this a bender who bought the upgrade
        // mid-suit would be wearing both at once for the rest of the duration, which
        // is 35 points rather than 20.
        player.removeEffect(diamond ? ModEffects.METAL_ARMOR : ModEffects.METAL_ARMOR_DIAMOND);

        player.addEffect(new MobEffectInstance(
                diamond ? ModEffects.METAL_ARMOR_DIAMOND : ModEffects.METAL_ARMOR,
                DURATION, 0, false, true, true));

        if (player.level() instanceof ServerLevel level) {
            Metal.clang(level, player.position(), 1.0F, 1.2F);
            Metal.spark(level, player.position().add(0.0, 1.0, 0.0), 30, 0.6);
        }
    }
}
