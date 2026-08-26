package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Metal. Three seconds to sharpen a rod of iron, which then hangs on the
 * crosshair until a left click throws it — and what it hits loses its entire armour
 * set.
 *
 * A real, visible PROJECTILE rather than an instant line trace, which is what fixes
 * the two things wrong with the first attempt: there was nothing at all to see, and
 * its aim tolerance of 0.4 blocks was so tight it essentially never found a target, so
 * the ability appeared to do nothing whatsoever. A thrown rod is visible the whole way
 * and hits what it actually passes through.
 *
 * It is still the most demanding shot in the mod — the hit radius is a third of what
 * anything else throws — but it is now demanding rather than impossible, and a miss is
 * visibly a miss.
 *
 * Both held shapes at once, like Fireball: the charge sharpens the rod and the armed
 * slot throws it.
 */
public class ArmorPierce implements ChargedAbility, TwoPhaseAbility {

    /** What it hits an UNARMOURED target for. */
    private static final float BARE_DAMAGE = 8.0F;

    /**
     * How the rod flies.
     *
     * The hit radius of 0.35 is the point of the ability: a third of an Air splinter's
     * and a fifth of a Stone wall's. "Needs perfect precision" has to be true of the
     * hitbox and not only of the description — but it is a hitbox now, so a shot that
     * is on target lands, where the old 0.4 aim-line tolerance almost never did.
     *
     * Fast and long-lived: it is a thrown rod, and it should reach.
     */
    private static final BendingProjectiles.Spec ROD = new BendingProjectiles.Spec(
            3.2, 60, 0.0F, 0.35, 0.1, BendingProjectiles.Style.STONE);

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
        return 600; // 30 seconds, and it starts on the throw
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

    /** Held until thrown: the rod waits on the crosshair as long as the bender likes. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    /**
     * The sharpened rod, drawn along the line it will fly.
     *
     * A short segment rather than a ball, so it reads as a rod being aimed — and so
     * the bender can see the ability is armed at all, which the first version never
     * showed anywhere.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8));
        Vec3 look = player.getLookAngle();

        for (double d = 0.0; d <= 1.2; d += 0.2) {
            Vec3 at = from.add(look.scale(d));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8));

        // The rod carries no damage of its own: what it does depends entirely on
        // whether the victim is wearing anything, and that is decided on the hit.
        BendingProjectiles.launch(player, from, player.getLookAngle(),
                ROD.withHitEntity(ArmorPierce::pierce));

        Metal.scrape(level, player.position(), 1.2F, 1.5F);
        Metal.spark(level, from, 12, 0.15);
    }

    /**
     * What the rod does to what it hits: takes the armour, or goes through them.
     *
     * Either/or, as the design asks — a victim in full diamond loses it and takes
     * nothing, and a victim in nothing takes 8. That is why the shot itself carries no
     * damage: the projectile system would otherwise apply it before this ran and both
     * would land.
     */
    private static void pierce(ServerPlayer owner, LivingEntity target) {
        ServerLevel level = (ServerLevel) target.level();

        if (stripArmor(target)) {
            Metal.clang(level, target.position(), 1.6F, 0.6F);
            Metal.spark(level, target.getEyePosition(), 40, 0.5);

            owner.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7Armour destroyed."), true);
            return;
        }

        // Nothing to take: it goes through them instead.
        target.hurt(owner.damageSources().indirectMagic(owner, owner),
                Metal.damage(owner.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA),
                        BARE_DAMAGE));
        Metal.spark(level, target.getEyePosition(), 25, 0.4);
    }

    /**
     * Destroys everything the target is wearing.
     *
     * DESTROYED rather than dropped, which is the design's word and the right one:
     * dropping would let the victim pick their own armour straight back up, and would
     * hand an attacker a free set off anybody they landed this on.
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

    /** Arming is the whole cast: the rod is sharpened and waits on the click. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Metal.spark((ServerLevel) player.level(), player.getEyePosition(), 15, 0.3);
    }
}
