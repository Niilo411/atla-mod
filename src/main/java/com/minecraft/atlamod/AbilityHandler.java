package com.minecraft.atlamod;

import com.minecraft.atlamod.network.SyncStatsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

public class AbilityHandler {

    // 1. THE DISPATCHER: This reads the ability name and fires the right code
    public static void executeAbility(ServerPlayer player, BendingData data, String ability) {
        if (ability == null || ability.isEmpty()) return;

        // ... (water check code) ...

        switch (ability.toLowerCase()) {
            case "fire leap":
                handleFireLeap(player, data);
                break;
            case "fire whip":
                handleFireWhip(player, data);
                break;
            case "fireball": // <--- THIS IS THE FIX
                handleFireball(player, data);
                break;
        }
    }

    // THE PHASE 2 DISPATCHER: Shoots whatever ability is currently active!
    public static void executeLeftClickPhase(ServerPlayer player, BendingData data) {
        String activeAbility = data.getActiveTwoPhaseAbility();
        if (activeAbility == null || activeAbility.isEmpty()) return;

        // Immediately clear it so the player can't spam left-click
        data.setActiveTwoPhaseAbility("");
        syncData(player, data);

        switch (activeAbility.toLowerCase()) {
            case "fireball":
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.phys.Vec3 look = player.getLookAngle();

                // Package the velocity
                net.minecraft.world.phys.Vec3 movement = new net.minecraft.world.phys.Vec3(look.x * 0.1, look.y * 0.1, look.z * 0.1);

                // Create a nerfed Large Fireball (Power level 1)
                net.minecraft.world.entity.projectile.LargeFireball bigFireball = new net.minecraft.world.entity.projectile.LargeFireball(
                        level, player, movement, 1
                );

                // Spawn it slightly in front of the player
                bigFireball.setPos(player.getX() + look.x, player.getEyeY(), player.getZ() + look.z);
                level.addFreshEntity(bigFireball);

                // Play the massive shoot sound
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.GHAST_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.0F);

                // Set a 2-second cooldown (40 ticks)
                data.setCooldown("fireball", 40);
                syncData(player, data);
                }

            // You can easily add Water Sphere, Air Cannon, etc., right here!
        }

    // 2. THE UTILITY: This handles Chi and XP automatically for EVERY ability!
    private static boolean consumeChiAndGiveXp(ServerPlayer player, BendingData data, int chiCost, int xpReward) {
        if (data.getCurrentChi() < chiCost) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot enough Chi! (Requires " + chiCost + ")"), true);
            return false;
        }

        data.consumeChi(chiCost);
        data.setXp(data.getXp() + xpReward);
        if (data.getXp() >= 200) { // Level up threshold
            data.setLevel(data.getLevel() + 1);
            data.setXp(0);
        }
        return true;
    }

    // 3. THE SYNC: Automatically saves data and updates the UI
    private static void syncData(ServerPlayer player, BendingData data) {
        player.setData(ModAttachments.BENDING_DATA, data);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SyncStatsPacket(data.getXp(), data.getLevel(), data.getCurrentChi()));
    }

    // ==========================================
    //          ABILITY LOGIC BELOW
    // ==========================================

    private static void handleFireLeap(ServerPlayer player, BendingData data) {
        if (data.isFireLeaping()) return;

        // This ONE line checks Chi (50) and gives XP (5)!
        if (!consumeChiAndGiveXp(player, data, 50, 5)) return;

        data.setFireLeaping(true);

        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 60, 0, false, false, false));

        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 1.9, 0.75, look.z * 1.9);
        player.hurtMarked = true;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

        syncData(player, data);
    }

    private static void handleFireWhip(ServerPlayer player, BendingData data) {
        // Cost: 50 Chi, Reward: 5 XP
        if (!consumeChiAndGiveXp(player, data, 50, 5)) return;

        ServerLevel serverLevel = (ServerLevel) player.level();
        net.minecraft.world.phys.Vec3 start = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.5F);

        boolean hitSomething = false;
        // Instantly shoots a 15-block raycast line of fire!
        for (int i = 1; i <= 15; i++) {
            net.minecraft.world.phys.Vec3 pos = start.add(look.scale(i));

            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.05);

            net.minecraft.world.phys.AABB hitbox = new net.minecraft.world.phys.AABB(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
            java.util.List<net.minecraft.world.entity.Entity> entities = serverLevel.getEntities(player, hitbox);

            for (net.minecraft.world.entity.Entity target : entities) {
                if (target instanceof net.minecraft.world.entity.LivingEntity livingTarget) {
                    livingTarget.hurt(player.damageSources().inFire(), 6.0F);
                    livingTarget.setRemainingFireTicks(100);
                    hitSomething = true;
                }
            }

            if (hitSomething) break;
        }

        syncData(player, data);
    }
    private static void handleFireball(ServerPlayer player, BendingData data) {
        // If they are already holding an ability, don't let them summon another one!
        if (data.getActiveTwoPhaseAbility() != null && !data.getActiveTwoPhaseAbility().isEmpty()) return;

// Check the 2-second universal cooldown!
        if (data.isOnCooldown("fireball")) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cFireball is on cooldown!"), true);
            return;
        }
        // Cost: 100 Chi, Reward: 10 XP
        if (!consumeChiAndGiveXp(player, data, 100, 10)) return;

        // Remember that we are holding a fireball!
        data.setActiveTwoPhaseAbility("fireball");
        syncData(player, data);

        // Play an ignition sound
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }
    private static final int FIRE_BREATH_CHI_PER_TICK = 4;

    // THE PHASE 3 DISPATCHER: For channeled/held abilities
    public static void executeAbilityHold(ServerPlayer player, BendingData data, String ability, boolean isHeld) {
        if (ability == null || ability.isEmpty()) return;

        switch (ability.toLowerCase()) {
            case "fire breath":
                if (isHeld) startFireBreath(player, data);
                else stopFireBreath(player, data);
                break;
        }
    }

    private static void startFireBreath(ServerPlayer player, BendingData data) {
        if (data.isBreathingFire()) return;
        if (data.getCurrentChi() < FIRE_BREATH_CHI_PER_TICK) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot enough Chi!"), true);
            return;
        }
        data.setBreathingFire(true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.7F);
        syncData(player, data);
    }

    private static void stopFireBreath(ServerPlayer player, BendingData data) {
        if (!data.isBreathingFire()) return;
        data.setBreathingFire(false);
        syncData(player, data);
    }

    // Called every tick from ServerEvents while the player is breathing fire
    public static void tickFireBreath(ServerPlayer player, BendingData data) {
        if (data.getCurrentChi() < FIRE_BREATH_CHI_PER_TICK) {
            stopFireBreath(player, data);
            return;
        }

        data.consumeChi(FIRE_BREATH_CHI_PER_TICK);

        // Trickle XP, same cadence as Meditate (2 xp/sec)
        if (player.tickCount % 20 == 0) {
            data.setXp(data.getXp() + 2);
            if (data.getXp() >= 200) {
                data.setLevel(data.getLevel() + 1);
                data.setXp(0);
            }
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.phys.Vec3 start = player.getEyePosition();
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();

            for (int i = 1; i <= 6; i++) {
                net.minecraft.world.phys.Vec3 pos = start.add(look.scale(i));

                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.3, 0.3, 0.3, 0.03);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 1, 0.2, 0.2, 0.2, 0.01);

                net.minecraft.world.phys.AABB hitbox = new net.minecraft.world.phys.AABB(
                        pos.x - 0.75, pos.y - 0.75, pos.z - 0.75,
                        pos.x + 0.75, pos.y + 0.75, pos.z + 0.75
                );
                java.util.List<net.minecraft.world.entity.Entity> entities = serverLevel.getEntities(player, hitbox);

                for (net.minecraft.world.entity.Entity target : entities) {
                    if (target instanceof net.minecraft.world.entity.LivingEntity livingTarget) {
                        livingTarget.hurt(player.damageSources().inFire(), 1.0F); // light per-tick damage since it's continuous
                        livingTarget.setRemainingFireTicks(60);
                    }
                }
            }
        }

        // Sync every 4 ticks instead of every tick — keeps the Chi bar responsive without flooding packets
        if (player.tickCount % 4 == 0) {
            syncData(player, data);
        }
    }
}
