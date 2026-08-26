package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Metal. Three seconds to sharpen a rod of iron, and then one shot that has
 * to be exactly on target — but takes a victim's entire armour set with it.
 *
 * The precision IS the price. Every other aimed ability in the mod is deliberately
 * forgiving, picking the nearest thing to the aim LINE within a couple of blocks so a
 * cast does not have to be pixel-perfect on something moving. This one is not: its
 * tolerance is a fifth of that, and a miss costs the whole 250 chi and thirty seconds.
 *
 * Against an unarmoured target it hits for 8 instead, so a perfect shot on something
 * that owns no armour is not simply wasted.
 */
public class ArmorPierce implements ChargedAbility {

    /** How far the rod carries, in blocks. */
    private static final double REACH = 25.0;

    /**
     * How far off the crosshair a target may be and still be hit.
     *
     * A fifth of what Lightning stun and Freeze allow. "Needs perfect precision" has
     * to be true of the hitbox and not only of the description — the same argument
     * Earth Splinters makes for its tight radius.
     */
    private static final double TOLERANCE = 0.4;

    /** What it hits an UNARMOURED target for. */
    private static final float BARE_DAMAGE = 8.0F;

    @Override
    public String getName() {
        return "Armor pierce";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 250;
    }

    @Override
    public int getXpReward() {
        return 25;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds
    }

    @Override
    public int getChargeTicks() {
        return 60; // 3 seconds
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 rod = player.getEyePosition().add(player.getLookAngle().scale(1.0 + progress));

        Metal.spark(level, rod, 3, 0.15);
    }

    /**
     * Deliberately NOT gated on having a target.
     *
     * Every other precision ability in the mod refuses the cast when nothing is in
     * view, so aiming at the sky costs nothing. This one charges for a miss on
     * purpose: an ability whose whole identity is that it is hard to land would have
     * no teeth if the game quietly refunded every attempt that failed.
     */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Metal.scrape(level, player.position(), 1.2F, 1.5F);

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) {
            // A clean miss. The rod is still thrown, so the shot is visibly spent
            // rather than the ability appearing not to have fired at all.
            Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(REACH));
            Metal.spark(level, end, 15, 0.4);
            return;
        }

        if (stripArmor(target)) {
            Metal.clang(level, target.position(), 1.6F, 0.6F);
            Metal.spark(level, target.getEyePosition(), 40, 0.5);
            return;
        }

        // Nothing to take: it goes through them instead.
        target.hurt(player.damageSources().indirectMagic(player, player),
                Metal.damage(data, BARE_DAMAGE));
        Metal.spark(level, target.getEyePosition(), 25, 0.4);
    }

    /**
     * Destroys everything the target is wearing.
     *
     * DESTROYED rather than dropped, which is the design's word and the right one:
     * dropping it would let the victim pick their own armour straight back up, and
     * would hand an attacker a free set off anybody they landed this on.
     *
     * @return false if they were wearing nothing at all
     */
    private static boolean stripArmor(LivingEntity target) {
        boolean tookSomething = false;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

            ItemStack worn = target.getItemBySlot(slot);
            if (worn.isEmpty()) continue;

            target.setItemSlot(slot, ItemStack.EMPTY);
            tookSomething = true;
        }

        return tookSomething;
    }
}
