package com.minecraft.atlamod.abilities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Everything the mod throws, advanced by hand rather than as real entities.
 *
 * A custom projectile entity would need its own EntityType and, more awkwardly, a
 * client renderer — and an entity that spawns without one takes the client down.
 * Tracking shots here keeps all of that out of the picture, and a mass of water or a
 * blade of air is better drawn as particles than as any model anyway.
 *
 * Written element-agnostic, like HeldBlocks: nothing here knows what it is carrying.
 * An ability describes its shot once as a {@link Spec} constant and the only thing
 * that differs between water and air is the {@link Style} used to draw it.
 */
public final class BendingProjectiles {

    /** Blocks travelled per tick is set by the caller; this is just the drag on it. */
    private static final double DRAG = 0.99;

    /** Gentle arc, far lighter than a thrown item, so shots fly fairly flat. */
    private static final double GRAVITY = 0.014;

    /**
     * Longest a shot may move between collision checks, in blocks.
     *
     * A tick's movement is walked in steps no bigger than this rather than tested
     * only at the far end. Air Splinters travel over 3 blocks a tick, which would
     * otherwise step clean through a wall — and past anything standing in front of
     * it — without ever being tested against either.
     */
    private static final double STEP = 0.9;

    /** The chip a stone shot is drawn from. Built once; it never varies. */
    private static final net.minecraft.core.particles.BlockParticleOption STONE_CHIP =
            new net.minecraft.core.particles.BlockParticleOption(
                    ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private BendingProjectiles() {
    }

    /** How a shot looks and sounds. */
    public enum Style {
        WATER,
        AIR,

        /**
         * Chips of real stone, drawn with BLOCK particles rather than a puff.
         * Those carry the stone texture and tumble, so a shot reads as a sharp
         * fragment of rock instead of a cloud with a damage number.
         */
        STONE,

        /**
         * A real block, carried by a FallingBlockEntity rather than drawn at all.
         * The only style that is not particles: Earth block throws the very block it
         * pulled out of the ground, and it has to look like one the whole way.
         */
        BLOCK,

        /** A streak of live current, drawn with sparks and a bright core. */
        LIGHTNING,

        /** A shard of ice, drawn with frost and a hard white core. */
        ICE,

        /**
         * A combustion charge: a hard white core trailing smoke.
         *
         * Unlike every other style this one is meant to leave a LINE behind it rather
         * than a puff at its current position — the white stripe is combustionbending's
         * signature, so the particles are given no velocity at all and simply hang
         * where the shot passed.
         */
        COMBUSTION,

        /** A blob of molten rock, trailing droplets and smoke as it goes. */
        LAVA
    }

    /**
     * One kind of shot. Abilities declare theirs once, as a constant.
     *
     * @param speed     blocks per tick
     * @param lifetime  ticks before it falls apart on its own
     * @param hitRadius how close something has to be to be caught
     * @param onHit     effect applied to whatever it hits, or null for none. A
     *                  supplier rather than an instance, because a MobEffectInstance
     *                  is stateful once applied and must not be shared between hits.
     */
    public record Spec(double speed, int lifetime, float damage, double hitRadius,
                       double knockback, Style style,
                       @Nullable Supplier<MobEffectInstance> onHit,
                       boolean piercesInvulnerability,
                       @Nullable BiConsumer<ServerLevel, Vec3> onImpact,
                       @Nullable BiConsumer<ServerPlayer, LivingEntity> onHitEntity) {

        /** A shot that only hits, with no lingering effect. */
        public Spec(double speed, int lifetime, float damage, double hitRadius,
                    double knockback, Style style) {
            this(speed, lifetime, damage, hitRadius, knockback, style, null, false, null, null);
        }

        /** A shot with a lingering effect, on vanilla's ordinary damage timing. */
        public Spec(double speed, int lifetime, float damage, double hitRadius,
                    double knockback, Style style,
                    @Nullable Supplier<MobEffectInstance> onHit) {
            this(speed, lifetime, damage, hitRadius, knockback, style, onHit, false, null, null);
        }

        /** A shot that pierces invulnerability frames, with no impact hook. */
        public Spec(double speed, int lifetime, float damage, double hitRadius,
                    double knockback, Style style,
                    @Nullable Supplier<MobEffectInstance> onHit,
                    boolean piercesInvulnerability) {
            this(speed, lifetime, damage, hitRadius, knockback, style,
                    onHit, piercesInvulnerability, null, null);
        }

        /**
         * The same shot, but with something left behind wherever it ends.
         *
         * {@code onImpact} runs on EVERY way a shot can finish — hitting a target,
         * hitting a wall, or simply running out of life — because "where it lands" has
         * to mean the same thing in all three. Lightning bolt's upgrade is the only
         * user so far, and calls down a real bolt there.
         */
        /** Everything except a hook on what was struck, which most shots do not need. */
        public Spec(double speed, int lifetime, float damage, double hitRadius,
                    double knockback, Style style,
                    @Nullable Supplier<MobEffectInstance> onHit,
                    boolean piercesInvulnerability,
                    @Nullable BiConsumer<ServerLevel, Vec3> onImpact) {
            this(speed, lifetime, damage, hitRadius, knockback, style,
                    onHit, piercesInvulnerability, onImpact, null);
        }

        /** The same shot, but doing something to whatever it strikes. */
        public Spec withHitEntity(@Nullable BiConsumer<ServerPlayer, LivingEntity> onHit) {
            return new Spec(speed, lifetime, damage, hitRadius, knockback, style,
                    this.onHit, piercesInvulnerability, onImpact, onHit);
        }

        public Spec withImpact(@Nullable BiConsumer<ServerLevel, Vec3> impact) {
            return new Spec(speed, lifetime, damage, hitRadius, knockback, style,
                    onHit, piercesInvulnerability, impact, onHitEntity);
        }
    }

    /** One shot on its way somewhere. */
    private static final class Shot {
        final UUID ownerId;
        final ServerLevel level;
        final Spec spec;
        Vec3 pos;
        Vec3 velocity;
        int ticksLeft;

        /** Set only for Style.BLOCK: what is being thrown, and the entity showing it. */
        @Nullable net.minecraft.world.level.block.state.BlockState carried;
        @Nullable net.minecraft.world.entity.item.FallingBlockEntity display;

        Shot(UUID ownerId, ServerLevel level, Spec spec, Vec3 pos, Vec3 velocity) {
            this.ownerId = ownerId;
            this.level = level;
            this.spec = spec;
            this.pos = pos;
            this.velocity = velocity;
            this.ticksLeft = spec.lifetime();
        }
    }

    /** Sends a shot on its way. */
    public static void launch(ServerPlayer owner, Vec3 from, Vec3 direction, Spec spec) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        IN_FLIGHT.add(new Shot(owner.getUUID(), level, spec, from,
                direction.normalize().scale(spec.speed())));
    }

    /**
     * Sends a real block on its way, still wearing the entity that was showing it.
     *
     * The display is ADOPTED rather than respawned: it is already alive and in the
     * right place, and spawning a new FallingBlockEntity would mean calling fall(),
     * which clears whatever block happens to occupy the spawn position.
     */
    public static void launchCarried(ServerPlayer owner, Vec3 from, Vec3 direction, Spec spec,
                                     net.minecraft.world.level.block.state.BlockState carried,
                                     @Nullable net.minecraft.world.entity.item.FallingBlockEntity display) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        Shot shot = new Shot(owner.getUUID(), level, spec, from,
                direction.normalize().scale(spec.speed()));
        shot.carried = carried;
        shot.display = display;
        IN_FLIGHT.add(shot);
    }

    /** Advances every shot in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (IN_FLIGHT.isEmpty()) return;

        Iterator<Shot> shots = IN_FLIGHT.iterator();
        while (shots.hasNext()) {
            Shot shot = shots.next();
            if (!advance(shot)) {
                shots.remove();
            }
        }
    }

    /** @return false once the shot is spent and should be dropped */
    private static boolean advance(Shot shot) {
        if (shot.ticksLeft-- <= 0) {
            burst(shot);
            return false;
        }

        // Walk this tick's movement in short steps, testing as we go. At these speeds
        // a single test at the destination misses thin walls and passing targets.
        double distance = shot.velocity.length();
        int steps = Math.max(1, (int) Math.ceil(distance / STEP));
        Vec3 step = shot.velocity.scale(1.0 / steps);

        for (int i = 0; i < steps; i++) {
            Vec3 next = shot.pos.add(step);

            if (shot.level.getBlockState(BlockPos.containing(next)).isSolid()) {
                burst(shot);
                return false;
            }

            shot.pos = next;

            if (strike(shot)) {
                return false;
            }
        }

        // Air shots fly FLAT. Everything else in the mod is a mass of something being
        // thrown and should arc, but a blade of compressed air visibly drooping over
        // its flight reads as the shot dying rather than travelling — and Air splinters
        // cross 45 blocks, far enough for the sag to be the first thing you notice.
        double drop = shot.spec.style() == Style.AIR ? 0.0 : GRAVITY;
        shot.velocity = shot.velocity.scale(DRAG).subtract(0.0, drop, 0.0);

        draw(shot);
        return true;
    }

    /** @return true if the shot hit something and is spent */
    private static boolean strike(Shot shot) {
        ServerPlayer owner = shot.level.getServer().getPlayerList().getPlayer(shot.ownerId);

        AABB hitbox = new AABB(shot.pos, shot.pos).inflate(shot.spec.hitRadius());
        for (Entity target : shot.level.getEntities(owner, hitbox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            // Lightning redirection: a bender standing ready CATCHES the bolt instead
            // of wearing it. This is the only place that knows a shot was about to
            // land on somebody, which is why the check has to live here.
            //
            // Returns without burst(), deliberately: the shot never landed, so its
            // impact hook must not fire — a caught Storm Caller bolt should not call
            // real lightning down on the catcher's head.
            if (shot.spec.style() == Style.LIGHTNING
                    && living instanceof ServerPlayer victim
                    && com.minecraft.atlamod.abilities.lightning.LightningRedirection
                            .absorb(victim, shot.spec.damage())) {
                return true;
            }

            if (owner != null) {
                // Vanilla ignores a second hit of equal size within ten ticks of the
                // first, which for an ability whose whole point is landing several
                // shots quickly would silently throw most of them away. Clearing the
                // timer first makes every splinter count.
                if (shot.spec.piercesInvulnerability()) {
                    living.invulnerableTime = 0;
                }

                living.hurt(owner.damageSources().indirectMagic(owner, owner), shot.spec.damage());
            }

            if (shot.spec.onHit() != null) {
                living.addEffect(shot.spec.onHit().get());
            }

            // Anything the ability wants to do to what it actually STRUCK, as opposed
            // to where it stopped. Armor pierce is the only user: taking somebody's
            // armour needs the victim, which the position-based onImpact cannot give.
            if (shot.spec.onHitEntity() != null && owner != null) {
                shot.spec.onHitEntity().accept(owner, living);
            }

            Vec3 push = shot.velocity.normalize().scale(shot.spec.knockback());
            living.setDeltaMovement(push.x, Math.max(0.25, push.y), push.z);
            // Players ignore server-side velocity unless it is explicitly pushed to them.
            living.hurtMarked = true;

            burst(shot);
            return true;
        }

        return false;
    }

    /** The shot itself: a tight clump, not a thin trail. */
    private static void draw(Shot shot) {
        double x = shot.pos.x;
        double y = shot.pos.y;
        double z = shot.pos.z;

        switch (shot.spec.style()) {
            case WATER -> {
                shot.level.sendParticles(ParticleTypes.SPLASH, x, y, z, 8, 0.22, 0.22, 0.22, 0.02);
                shot.level.sendParticles(ParticleTypes.FALLING_WATER, x, y, z, 4, 0.2, 0.2, 0.2, 0.0);
                shot.level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 3, 0.15, 0.15, 0.15, 0.01);
            }
            case AIR -> {
                shot.level.sendParticles(ParticleTypes.CLOUD, x, y, z, 5, 0.1, 0.1, 0.1, 0.0);
                shot.level.sendParticles(ParticleTypes.SMALL_GUST, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            case STONE -> {
                // Real block chips, tightly clustered and barely moving, so they read
                // as one sharp fragment travelling rather than a trail of dust.
                shot.level.sendParticles(STONE_CHIP, x, y, z, 4, 0.04, 0.04, 0.04, 0.0);
            }
            case LIGHTNING -> {
                // A bright core with sparks thrown off it, so the shot reads as a
                // streak of current rather than a glowing dot.
                shot.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 6, 0.12, 0.12, 0.12, 0.06);
                shot.level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            case ICE -> {
                // Tight and hard, with a short trail of frost falling off it — a
                // shard travelling, not a cloud of snow.
                shot.level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 3, 0.06, 0.06, 0.06, 0.0);
                shot.level.sendParticles(ParticleTypes.ITEM_SNOWBALL, x, y, z, 2, 0.05, 0.05, 0.05, 0.0);
            }
            case COMBUSTION -> {
                // Motionless, so the stripe stays exactly where the shot went rather
                // than drifting apart behind it.
                shot.level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                shot.level.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.04, 0.04, 0.04, 0.0);
            }
            case LAVA -> {
                // Molten, and visibly dripping: the LAVA particle already falls on its
                // own, so a blob leaves a short trail behind it without a second style
                // having to be invented for one.
                shot.level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 0.12, 0.12, 0.12, 0.0);
                shot.level.sendParticles(ParticleTypes.FLAME, x, y, z, 3, 0.1, 0.1, 0.1, 0.01);
                shot.level.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.1, 0.1, 0.1, 0.01);
            }
            case BLOCK -> {
                // Nothing is drawn: the block IS the entity, just moved along.
                if (shot.display != null && shot.display.isAlive()) {
                    shot.display.setNoGravity(true);
                    shot.display.setDeltaMovement(Vec3.ZERO);
                    // Its own timer would land it as a block or drop it as an item.
                    shot.display.time = 0;
                    shot.display.setPos(x, y - 0.5, z);
                    // FALLING_BLOCK syncs its position once a SECOND (updateInterval 20).
                    // A thrown block without this arrives in one-second teleports.
                    shot.display.hasImpulse = true;
                }
            }
        }
    }

    /**
     * Sets a thrown block down where it stopped.
     *
     * The block was taken OUT of the world to be thrown, so it has to go back into it
     * — a throw that simply deleted whatever it was carrying would make the ability a
     * quiet way of destroying terrain. If the landing spot is somehow occupied it is
     * dropped as an item instead, which is still not losing it.
     */
    private static void landBlock(Shot shot, double x, double y, double z) {
        if (shot.display != null && shot.display.isAlive()) {
            shot.display.discard();
        }
        if (shot.carried == null) return;

        BlockPos at = BlockPos.containing(x, y, z);
        if (shot.level.getBlockState(at).canBeReplaced()) {
            shot.level.setBlockAndUpdate(at, shot.carried);
        } else {
            net.minecraft.world.level.block.Block.popResource(
                    shot.level, at, new net.minecraft.world.item.ItemStack(shot.carried.getBlock()));
        }

        shot.level.playSound(null, x, y, z,
                shot.carried.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    /** Where it comes apart. */
    private static void burst(Shot shot) {
        double x = shot.pos.x;
        double y = shot.pos.y;
        double z = shot.pos.z;

        // Hooked here rather than at each of the three call sites, because burst() is
        // already the single place a spent shot funnels through however it ended —
        // target, wall, or running out of life.
        if (shot.spec.onImpact() != null) {
            shot.spec.onImpact().accept(shot.level, shot.pos);
        }

        switch (shot.spec.style()) {
            case WATER -> {
                shot.level.sendParticles(ParticleTypes.SPLASH, x, y, z, 40, 0.6, 0.6, 0.6, 0.12);
                shot.level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 15, 0.5, 0.5, 0.5, 0.05);
                shot.level.playSound(null, x, y, z,
                        SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.1F);
            }
            case STONE -> {
                shot.level.sendParticles(STONE_CHIP, x, y, z, 25, 0.25, 0.25, 0.25, 0.16);
                shot.level.playSound(null, x, y, z,
                        SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
            }
            case LIGHTNING -> {
                shot.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 45, 0.5, 0.5, 0.5, 0.35);
                shot.level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.3, 0.3, 0.3, 0.05);
                shot.level.playSound(null, x, y, z,
                        SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9F, 1.5F);
            }
            case COMBUSTION -> {
                // The blast itself is left to whatever explosion the ability sets off
                // through its impact hook; this is only the flash.
                shot.level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                shot.level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 0.5, 0.5, 0.5, 0.05);
            }
            case ICE -> {
                shot.level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 30, 0.35, 0.35, 0.35, 0.2);
                shot.level.sendParticles(ParticleTypes.ITEM_SNOWBALL, x, y, z, 15, 0.3, 0.3, 0.3, 0.25);
                shot.level.playSound(null, x, y, z,
                        SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.4F);
            }
            case LAVA -> {
                // Whatever the shot leaves behind is its impact hook's business — real
                // lava for a throw, a temporary puddle for a rain drop. This is only
                // the splash.
                shot.level.sendParticles(ParticleTypes.LAVA, x, y, z, 25, 0.4, 0.4, 0.4, 0.0);
                shot.level.sendParticles(ParticleTypes.FLAME, x, y, z, 20, 0.4, 0.4, 0.4, 0.06);
                shot.level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 10, 0.4, 0.4, 0.4, 0.03);
                shot.level.playSound(null, x, y, z,
                        SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.8F);
            }
            case BLOCK -> landBlock(shot, x, y, z);
            case AIR -> {
                // Scaled off the shot's own hit radius, which is already the mod's
                // measure of how big the thing is: an Air splinter pops, an Air
                // cannon round bursts. Water is left on its fixed figures rather
                // than being retuned for the sake of it.
                double size = shot.spec.hitRadius();

                shot.level.sendParticles(ParticleTypes.CLOUD, x, y, z,
                        (int) (12 + 40 * size), size * 0.6, size * 0.6, size * 0.6, 0.1);
                shot.level.sendParticles(
                        size >= 1.0 ? ParticleTypes.GUST_EMITTER_LARGE : ParticleTypes.GUST,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                shot.level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_BURST,
                        SoundSource.PLAYERS, (float) (0.5 + 0.5 * size), (float) (1.8 - 0.6 * size));
            }
        }
    }

    /**
     * Drops every shot belonging to a level being unloaded.
     *
     * These are held in a plain static list rather than by the world, so nothing else
     * would ever clear them — a shot fired into a dimension that then unloaded would
     * keep a reference to a dead ServerLevel for as long as the server ran.
     */
    public static void forgetLevel(ServerLevel level) {
        IN_FLIGHT.removeIf(shot -> shot.level == level);
    }
}
