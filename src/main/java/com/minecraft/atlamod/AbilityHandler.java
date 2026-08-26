package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityRegistry;
import com.minecraft.atlamod.abilities.AbilitySupport;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.PassiveAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.network.chat.Component;
import com.minecraft.atlamod.network.ChargeStatusPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Thin dispatcher. It owns the rules that apply to EVERY ability — cooldown gating,
 * chi cost, XP reward, arming/releasing two-phase abilities, channeling lifecycle,
 * and syncing back to the client — then hands off to the ability's own logic.
 *
 * Ability effects themselves live in com.minecraft.atlamod.abilities.*; adding one
 * should never require touching this file.
 */
public class AbilityHandler {

    // ==========================================
    //  PHASE 1: instant cast (slot key pressed)
    // ==========================================
    /**
     * How long an ability's cooldown actually is for this player.
     *
     * Sound boosting takes a quarter off every AIR and SOUND cooldown. Applied here
     * rather than by the abilities because cooldowns are stamped in five different
     * places, none of which an ability class touches — asking each one to shorten its
     * own would be a rule the next ability added would quietly break.
     */
    private static int cooldownFor(BendingData data, Ability ability) {
        int base = ability.getCooldownTicks();
        if (base <= 0) return base;

        return boostable(ability) ? com.minecraft.atlamod.abilities.sound.Sound.shorten(data, base) : base;
    }

    /** The same quarter off an ability's charge time. */
    private static int chargeTicksFor(BendingData data, ChargedAbility ability) {
        int base = ability.getChargeTicks();
        if (base <= 0) return base;

        return boostable(ability) ? com.minecraft.atlamod.abilities.sound.Sound.shorten(data, base) : base;
    }

    /** Whether Sound boosting reaches this ability: air and sound abilities only. */
    private static boolean boostable(Ability ability) {
        String element = com.minecraft.atlamod.abilities.ElementPaths.elementOf(ability.getName());
        return "air".equals(element) || "sound".equals(element);
    }

    public static void executeAbility(ServerPlayer player, BendingData data, String abilityName) {
        Ability ability = AbilityRegistry.get(abilityName);
        if (ability == null) return;

        // Deafen locks its victims out of bending entirely for a few seconds. Checked
        // at the very top, before anything is spent or stamped.
        if (data.isBendingLocked()) {
            player.displayClientMessage(Component.literal(
                    "§cYou cannot bend right now! (" + ((data.getBendingLockedTicks() + 19) / 20) + "s)"), true);
            return;
        }

        // Held shapes do NOT start here — they come in through executeAbilityHold().
        // The client can't tell the shapes apart, so it fires UseAbilityPacket AND
        // AbilityHoldPacket on the same key press. Without this guard the cast path
        // would run the ability immediately, defeating a charge-up entirely, and for
        // a channel would stamp its cooldown while doing nothing visible — leaving
        // the ability looking dead and permanently cooling.
        // Passives are never cast at all: being equipped is the whole activation.
        if (ability instanceof ChanneledAbility || ability instanceof ChargedAbility
                || ability instanceof PassiveAbility) return;

        dismountRide(player, ability);
        performCast(player, data, ability);
    }

    /**
     * Casting anything else gets you off whatever you are riding first — Air Scooter
     * or Water Surf.
     *
     * Chosen over refusing the cast: bending from a ride would mean bending while sat
     * on a moving entity the server is steering, which is a lot of surface for odd
     * interactions (rooting channels that cannot root a passenger, flight abilities
     * fighting the seat), while blocking abilities outright risks a player who feels
     * stuck and cannot work out why nothing fires. Stepping off is unambiguous, and
     * the ride is free to get back onto.
     */
    private static void dismountRide(ServerPlayer player, Ability ability) {
        if (ability instanceof com.minecraft.atlamod.abilities.air.AirScooter
                || ability instanceof com.minecraft.atlamod.abilities.water.WaterSurf
                || ability instanceof com.minecraft.atlamod.abilities.earth.EarthDig) return;
        com.minecraft.atlamod.abilities.Rides.stop(player);
    }

    /**
     * The shared cast: cooldown gate, precondition, chi, XP, effect, cooldown, sync.
     *
     * Split out from executeAbility so a charge that finishes winding up lands on
     * exactly the same path an instant cast does, rather than a parallel copy that
     * could drift from it.
     */
    private static void performCast(ServerPlayer player, BendingData data, Ability ability) {
        // Switching a toggle off is not a cast, and is handled before ANY of the
        // gates below. A toggle that outlasts its own cooldown — Tornado runs 30
        // seconds behind a 10 second one — would otherwise be uncancellable for as
        // long as the cooldown had left to run, and nobody should pay chi to stop
        // doing something.
        if (ability.isActive(player, data)) {
            ability.deactivate(player, data);
            AbilitySupport.syncData(player, data);
            return;
        }

        if (ability.getCooldownTicks() > 0 && data.isOnCooldown(ability.getKey())) {
            // WITH the seconds left, the same as the charge and channel paths already
            // showed. Without them a long cooldown is indistinguishable from a broken
            // one: Earth sink says nothing for two minutes and then quietly works, and
            // the only honest reading of that from the outside is "it never comes back".
            int secondsLeft = (data.getCooldownRemaining(ability.getKey()) + 19) / 20;
            player.displayClientMessage(Component.literal(
                    "§c" + ability.getName() + " is on cooldown! (" + secondsLeft + "s)"), true);
            return;
        }

        // Per-ability precondition runs before chi is spent, so a blocked cast is free.
        if (!ability.canStart(player, data)) return;

        // Water has to be found before chi is spent, so a bender caught dry loses
        // nothing for trying.
        if (ability.requiresWater()
                && !com.minecraft.atlamod.abilities.WaterSupply.tryConsume(player)) {
            return;
        }

        if (!AbilitySupport.consumeChiAndGiveXp(player, data, ability.getChiCost(data), ability.getXpReward())) {
            return;
        }

        // Two-phase abilities arm here and fire on the next left click.
        if (ability instanceof TwoPhaseAbility twoPhase) {
            data.setActiveTwoPhaseAbility(ability.getKey());
            data.setTwoPhaseTicks(twoPhase.getArmedDurationTicks());
            data.setTwoPhaseShots(twoPhase.getShots());
        }

        ability.execute(player, data);

        // Two-phase cooldowns start on release instead — see TwoPhaseAbility.
        if (ability.getCooldownTicks() > 0 && !(ability instanceof TwoPhaseAbility)) {
            data.setCooldown(ability.getKey(), cooldownFor(data, ability));
        }

        AbilitySupport.syncData(player, data);
    }

    /**
     * Arms a two-phase ability WITHOUT casting it.
     *
     * The ordinary route into the armed state is performCast, which also gates on
     * cooldown and spends chi. Lightning redirection needs the state without either:
     * catching a bolt somebody else threw is not a cast the catcher paid for, and it
     * happens when the bolt arrives rather than when a key is pressed.
     */
    public static void armTwoPhase(ServerPlayer player, BendingData data, TwoPhaseAbility ability) {
        data.setActiveTwoPhaseAbility(ability.getKey());
        data.setTwoPhaseTicks(ability.getArmedDurationTicks());
        data.setTwoPhaseShots(ability.getShots());

        AbilitySupport.syncData(player, data);
        syncChargeStatus(player, data);
    }

    /**
     * Ends whatever channel is running, exactly as releasing the key would — cooldown,
     * rooting and onStop included.
     *
     * Public so an ability can end its own channel in response to something that
     * happened in the world rather than to the key coming up. Every exit route going
     * through stopChannel is what keeps the cooldown uniform.
     */
    public static void endChannel(ServerPlayer player, BendingData data) {
        String key = data.getActiveChanneledAbility();
        if (key.isEmpty()) return;

        if (AbilityRegistry.get(key) instanceof ChanneledAbility channeled) {
            stopChannel(player, data, channeled);
        } else {
            // Unknown ability recorded — clear the stuck flag rather than leaving the
            // player permanently mid-channel.
            data.setActiveChanneledAbility("");
            data.setChannelTicks(0);
        }
    }

    // ==========================================
    //  PHASE 2: release an armed two-phase ability (left click)
    // ==========================================
    public static void executeLeftClickPhase(ServerPlayer player, BendingData data) {
        String armedKey = data.getActiveTwoPhaseAbility();
        if (armedKey.isEmpty()) return;

        Ability ability = AbilityRegistry.get(armedKey);
        if (!(ability instanceof TwoPhaseAbility twoPhase)) {
            // Unknown ability recorded — clear the stuck flag.
            data.setActiveTwoPhaseAbility("");
            data.setTwoPhaseTicks(0);
            data.setTwoPhaseShots(0);
            return;
        }

        twoPhase.onRelease(player, data);

        // An ability may be good for several clicks (Water Bullets fires three). The
        // slot stays armed until they are all spent, so a partly used one is still
        // held rather than thrown away by its first shot.
        int shotsLeft = data.getTwoPhaseShots() - 1;
        data.setTwoPhaseShots(shotsLeft);

        if (shotsLeft <= 0) {
            data.setActiveTwoPhaseAbility("");
            data.setTwoPhaseTicks(0);
            data.setTwoPhaseShots(0);

            // The cooldown waits for the last shot, not the first.
            if (ability.getCooldownTicks() > 0) {
                data.setCooldown(ability.getKey(), cooldownFor(data, ability));
            }
        }

        AbilitySupport.syncData(player, data);
        syncChargeStatus(player, data);
    }

    // ==========================================
    //  PHASE 3: held abilities — channels and charges (slot key held)
    // ==========================================
    public static void executeAbilityHold(ServerPlayer player, BendingData data, String abilityName, boolean isHeld) {
        // Only a PRESS is refused while locked out. A key RELEASE has to get through,
        // or a channel that was already running when the lockout landed could never be
        // let go of and would drain chi until it ran dry.
        if (isHeld && data.isBendingLocked()) {
            player.displayClientMessage(Component.literal(
                    "§cYou cannot bend right now! (" + ((data.getBendingLockedTicks() + 19) / 20) + "s)"), true);
            return;
        }

        Ability ability = AbilityRegistry.get(abilityName);

        // Only when a held ability is STARTING. A key release must never dismount —
        // it arrives for every ability the player lets go of, including the scooter's
        // own key, which would turn the toggle back off the instant it was pressed.
        if (isHeld && ability != null) {
            dismountRide(player, ability);
        }

        if (ability instanceof ChanneledAbility channeled) {
            if (isHeld) {
                startChannel(player, data, channeled);
            } else {
                stopChannel(player, data, channeled);
            }
            return;
        }

        if (ability instanceof ChargedAbility charged) {
            if (isHeld) {
                startCharge(player, data, charged);
            } else {
                cancelCharge(player, data, charged);
            }
        }
    }

    private static void startCharge(ServerPlayer player, BendingData data, ChargedAbility ability) {
        // A toggle that is already ON goes off immediately, and this is checked at the
        // very TOP — before the held-ability guard, the cooldown and the chi check —
        // exactly as performCast does it. Switching something off must never be
        // refused, must never be charged for, and must never make the player sit
        // through the wind-up again: Lightning ball costs a second to send out, but
        // calling it back should be instant.
        if (ability.isActive(player, data)) {
            ability.deactivate(player, data);
            AbilitySupport.syncData(player, data);
            return;
        }

        // One held ability at a time, of either shape.
        if (data.isCharging() || data.isChanneling()) return;

        if (ability.getCooldownTicks() > 0 && data.isOnCooldown(ability.getKey())) {
            int secondsLeft = (data.getCooldownRemaining(ability.getKey()) + 19) / 20;
            player.displayClientMessage(Component.literal(
                    "§c" + ability.getName() + " is on cooldown! (" + secondsLeft + "s)"), true);
            return;
        }

        if (!ability.canStart(player, data)) return;

        // Chi is only CHECKED here — it is spent when the cast actually lands, so
        // winding up and letting go early costs the player nothing.
        if (data.getCurrentChi() < ability.getChiCost(data)) {
            player.displayClientMessage(Component.literal(
                    "§cNot enough Chi! (Requires " + ability.getChiCost(data) + ")"), true);
            return;
        }

        data.setActiveChargingAbility(ability.getKey());
        data.setChargeTicks(0);
        ability.onChargeStart(player, data);
        syncChargeStatus(player, data);
        AbilitySupport.syncData(player, data);
    }

    /** Key released before the charge finished: drop it, spend nothing. */
    /**
     * Key released. Normally that throws the charge away, but an ability that fires
     * on release goes off at whatever strength it reached.
     */
    private static void cancelCharge(ServerPlayer player, BendingData data, ChargedAbility ability) {
        // A charge that already fired cleared itself, so the eventual key release
        // lands here and finds nothing — which is what stops it double-firing.
        if (!data.getActiveChargingAbility().equals(ability.getKey())) return;

        int charged = data.getChargeTicks();

        data.setActiveChargingAbility("");
        data.setChargeTicks(0);

        if (ability.firesOnRelease() && charged >= ability.getMinimumChargeTicks()) {
            data.setLastChargeTicks(charged);
            performCast(player, data, ability);
            syncChargeStatus(player, data);
            return;
        }

        ability.onChargeCancel(player, data);
        syncChargeStatus(player, data);
        AbilitySupport.syncData(player, data);
    }

    /**
     * Called every tick from ServerEvents while a charge is winding up. Fires the
     * ability through the ordinary cast path the moment it is full.
     */
    public static void tickCharging(ServerPlayer player, BendingData data) {
        Ability ability = AbilityRegistry.get(data.getActiveChargingAbility());
        if (!(ability instanceof ChargedAbility charged)) {
            // Unknown ability recorded — clear the stuck flag.
            data.setActiveChargingAbility("");
            data.setChargeTicks(0);
            return;
        }

        int held = data.getChargeTicks() + 1;
        data.setChargeTicks(held);

        if (held < chargeTicksFor(data, charged)) {
            charged.onChargeTick(player, data, held);
            // Every other tick is smooth enough for the meter without flooding packets.
            if (held % 2 == 0) syncChargeStatus(player, data);
            return;
        }

        // Full. Clear the charge BEFORE casting, so the key release that follows
        // finds nothing to cancel and the ability can't fire twice.
        data.setActiveChargingAbility("");
        data.setChargeTicks(0);
        data.setLastChargeTicks(held);
        performCast(player, data, charged);
        syncChargeStatus(player, data);
    }

    private static void startChannel(ServerPlayer player, BendingData data, ChanneledAbility ability) {
        // One held ability at a time, of either shape.
        if (data.isChanneling() || data.isCharging()) return;

        if (ability.getCooldownTicks() > 0 && data.isOnCooldown(ability.getKey())) {
            int secondsLeft = (data.getCooldownRemaining(ability.getKey()) + 19) / 20;
            player.displayClientMessage(Component.literal(
                    "§c" + ability.getName() + " is on cooldown! (" + secondsLeft + "s)"), true);
            return;
        }

        // Per-ability precondition, the same one performCast checks and in the same
        // place. This was missing until Earth wall needed it, which meant Water Surf
        // and Water Sphere's "you must be in water" tests had never actually run —
        // only Water Heal appeared to work, and that was its canContinue stopping the
        // channel a tick after it started rather than the refusal doing its job.
        if (!ability.canStart(player, data)) return;

        // A gate, not a cost: nothing is deducted for meeting it, and the channel
        // keeps running below this figure once it is up.
        int requiredChi = ability.getMinimumChiToStart(data);
        if (data.getCurrentChi() < requiredChi) {
            player.displayClientMessage(Component.literal(
                    "§cNot enough Chi! (Requires " + requiredChi + ")"), true);
            return;
        }

        // Water once per activation, not per tick: a channel is one use of the
        // ability, and draining a canteen unit every tick would empty it in a second.
        if (ability.requiresWater()
                && !com.minecraft.atlamod.abilities.WaterSupply.tryConsume(player)) {
            return;
        }

        data.setActiveChanneledAbility(ability.getKey());
        data.setChannelTicks(0);
        ability.onStart(player, data);
        setRooted(player, ability.rootsPlayer(data));
        AbilitySupport.syncData(player, data);
    }

    /**
     * Ends a channel. Every route out of a channel comes through here — releasing the
     * key, running dry on chi, or hitting the duration cap — so the cooldown applies
     * uniformly and can't be dodged by picking a particular way to stop.
     */
    private static void stopChannel(ServerPlayer player, BendingData data, ChanneledAbility ability) {
        // Ignore a key-release for an ability that isn't the one currently channeling.
        // This is also what stops an auto-stopped channel from taking a second cooldown
        // when the player eventually lets go of the key.
        if (!data.getActiveChanneledAbility().equals(ability.getKey())) return;

        data.setActiveChanneledAbility("");
        data.setChannelTicks(0);

        if (ability.getCooldownTicks() > 0) {
            data.setCooldown(ability.getKey(), cooldownFor(data, ability));
        }

        setRooted(player, false);
        ability.onStop(player, data);
        AbilitySupport.syncData(player, data);
    }

    /**
     * Called every tick from ServerEvents while any channel is active.
     * Handles the duration cap, drain, XP and sync cadence so channeled abilities
     * only implement their effect.
     */
    public static void tickChanneled(ServerPlayer player, BendingData data) {
        Ability ability = AbilityRegistry.get(data.getActiveChanneledAbility());
        if (!(ability instanceof ChanneledAbility channeled)) {
            // Unknown ability recorded (e.g. removed from the registry) — clear the stuck flag.
            data.setActiveChanneledAbility("");
            data.setChannelTicks(0);
            return;
        }

        // Duration cap is checked BEFORE the effect runs, so a 200-tick cap yields
        // exactly 200 ticks of effect and stops on the 201st.
        int maxDuration = channeled.getMaxDurationTicks();
        if (maxDuration > 0 && data.getChannelTicks() >= maxDuration) {
            stopChannel(player, data, channeled);
            return;
        }

        // A condition the channel depends on can lapse while the key is still held.
        if (!channeled.canContinue(player, data)) {
            stopChannel(player, data, channeled);
            return;
        }

        int chiThisTick = chiCostForTick(channeled, data, player.tickCount);
        if (data.getCurrentChi() < chiThisTick) {
            stopChannel(player, data, channeled);
            return;
        }

        data.consumeChi(chiThisTick);

        AbilitySupport.grantXp(data, xpForTick(channeled, data));

        if (channeled.rootsPlayer(data)) holdStill(player);

        // The wind-up is a quiet opening stretch, not a pause: chi is already gone by
        // this point and the duration cap is already counting, so holding the key
        // through it is a real commitment rather than a free run-up.
        if (channeled.isReady(data)) {
            channeled.onTick(player, data);
        } else {
            channeled.onWindupTick(player, data, data.getChannelTicks() + 1);
        }

        data.setChannelTicks(data.getChannelTicks() + 1);

        // Sync every 4 ticks instead of every tick — keeps the Chi bar responsive
        // without flooding packets.
        if (player.tickCount % 4 == 0) {
            AbilitySupport.syncData(player, data);
        }
    }

    /**
     * How much chi this particular tick of a channel costs.
     *
     * Rates are authored per second but spent per tick, and 20 rarely divides the
     * rate evenly (25/sec is 1.25/tick). Rounding each tick would drift, so this
     * differences a running total instead: any 20 consecutive ticks sum to exactly
     * getChiPerSecond(), while individual ticks alternate (25/sec spends 1 chi on
     * fifteen ticks and 2 chi on five). The chi bar still drains smoothly.
     */
    private static int chiCostForTick(ChanneledAbility ability, BendingData data, int tick) {
        long rate = ability.getChiPerSecond(data);
        long t = Math.max(0, tick);
        return (int) ((rate * (t + 1)) / 20L - (rate * t) / 20L);
    }

    /**
     * How much XP this particular tick of a channel earns.
     *
     * The same differencing trick as chi, and for the same reason: rates are authored
     * per second but paid per tick. It runs off the CHANNEL's own tick count rather
     * than the player's, which is what lets a rate below 1 work at all — Air scooter
     * earns 0.5 a second, meaning one XP on the 40th tick of the channel and nothing
     * on the 39 before it. A rate of 2 still totals 2 over any second; it is simply
     * trickled through the second now instead of landing in a lump.
     */
    private static int xpForTick(ChanneledAbility ability, BendingData data) {
        double rate = ability.getXpPerSecond();
        if (rate <= 0.0) return 0;

        long t = Math.max(0, data.getChannelTicks());
        return (int) (Math.floor(rate * (t + 1) / 20.0) - Math.floor(rate * t / 20.0));
    }

    /**
     * Whether the player's active channel should block this particular damage.
     * Consulted by the LivingIncomingDamageEvent handler in ServerEvents.
     *
     * Kept here rather than in ServerEvents so the answer stays driven by the
     * registry: any future channel (Earth Armor) gets this for free by overriding
     * grantsInvulnerability, or ChanneledAbility#blocks when it needs to be choosier
     * about what it stops.
     */
    public static boolean blocksDamage(BendingData data, DamageSource source) {
        if (!data.isChanneling()) return false;

        Ability ability = AbilityRegistry.get(data.getActiveChanneledAbility());
        if (!(ability instanceof ChanneledAbility channeled)) return false;

        // The one thing no shield gets a say in. BYPASSES_INVULNERABILITY is the void
        // and /kill, which should still work on a player holding anything at all.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;

        // Everything else is the ability's own call. The default still lets fall
        // damage through, so the two full shields are unchanged.
        return channeled.blocks(data, source);
    }

    /**
     * Pushes the current charge/armed state to the player's HUD.
     *
     * Reads the state rather than being told it, so callers only have to say "this
     * changed" and can't disagree with each other about what to display.
     */
    private static void syncChargeStatus(ServerPlayer player, BendingData data) {
        String charging = data.getActiveChargingAbility();
        if (!charging.isEmpty()) {
            Ability ability = AbilityRegistry.get(charging);
            // The SHORTENED total, so the meter fills against the charge the player is
            // actually serving rather than the ability's unboosted figure.
            int total = (ability instanceof ChargedAbility charged) ? chargeTicksFor(data, charged) : 0;
            String label = ability != null ? ability.getName() : charging;

            PacketDistributor.sendToPlayer(player,
                    new ChargeStatusPacket(label, data.getChargeTicks(), total, false));
            return;
        }

        String armed = data.getActiveTwoPhaseAbility();
        if (!armed.isEmpty()) {
            Ability ability = AbilityRegistry.get(armed);
            String label = ability != null ? ability.getName() : armed;

            // A window shows time draining; several shots show shots remaining; a
            // single-shot ability just shows ready.
            int window = (ability instanceof TwoPhaseAbility twoPhase)
                    ? twoPhase.getArmedDurationTicks() : 0;
            int shots = (ability instanceof TwoPhaseAbility twoPhase2)
                    ? twoPhase2.getShots() : 1;

            if (window > 0) {
                PacketDistributor.sendToPlayer(player,
                        new ChargeStatusPacket(label, data.getTwoPhaseTicks(), window, true));
            } else if (shots > 1) {
                PacketDistributor.sendToPlayer(player,
                        new ChargeStatusPacket(label + " x" + data.getTwoPhaseShots(),
                                data.getTwoPhaseShots(), shots, true));
            } else {
                PacketDistributor.sendToPlayer(player, new ChargeStatusPacket(label, 1, 1, true));
            }
            return;
        }

        PacketDistributor.sendToPlayer(player, new ChargeStatusPacket("", 0, 0, false));
    }

    /** Tells the client whether an ability is holding the player still. */
    private static void setRooted(ServerPlayer player, boolean rooted) {
        PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.RootedPacket(rooted));
    }

    /**
     * Pins the player where they stand while a rooting channel runs.
     *
     * Horizontal motion is zeroed but downward motion is left alone, so a shield does
     * not also make the player hover — being rooted should not mean being suspended
     * in mid-air if the ground is taken out from under them.
     */
    private static void holdStill(ServerPlayer player) {
        net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
        // Players ignore server-side velocity unless it is explicitly pushed to them.
        player.hurtMarked = true;
    }

    /**
     * Drives an armed two-phase ability each tick: lets it draw whatever it is
     * holding, and runs down the window for those that have one.
     *
     * Expiry applies the cooldown, because the chi was already spent when the ability
     * was armed — fumbling the window costs the cast, which is the whole reason a
     * window exists.
     */
    public static void tickArmedTwoPhase(ServerPlayer player, BendingData data) {
        String armedKey = data.getActiveTwoPhaseAbility();
        if (armedKey.isEmpty()) return;

        Ability ability = AbilityRegistry.get(armedKey);
        if (!(ability instanceof TwoPhaseAbility twoPhase)) {
            // Unknown ability recorded — clear the stuck flag.
            data.setActiveTwoPhaseAbility("");
            data.setTwoPhaseTicks(0);
            data.setTwoPhaseShots(0);
            return;
        }

        twoPhase.onArmedTick(player, data);

        if (twoPhase.getArmedDurationTicks() <= 0) return; // waits indefinitely

        int left = data.getTwoPhaseTicks() - 1;
        data.setTwoPhaseTicks(left);

        // Kept in step with the HUD meter, which drains as the window closes.
        if (left % 2 == 0) syncChargeStatus(player, data);

        if (left <= 0) {
            data.setActiveTwoPhaseAbility("");
            data.setTwoPhaseTicks(0);
            data.setTwoPhaseShots(0);

            if (ability.getCooldownTicks() > 0) {
                data.setCooldown(ability.getKey(), cooldownFor(data, ability));
            }

            twoPhase.onArmedExpire(player, data);
            AbilitySupport.syncData(player, data);
            syncChargeStatus(player, data);
        }
    }
}
