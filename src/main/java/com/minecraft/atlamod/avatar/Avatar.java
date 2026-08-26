package com.minecraft.atlamod.avatar;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.network.SyncAvatarPacket;
import com.minecraft.atlamod.network.SyncBendingDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything the Avatar is: granting and revoking the title, the three lives, the
 * cycle that hands it on through earth -> fire -> air -> water, and the last-stand
 * buffs that come up when an Avatar is nearly dead.
 *
 * The state is split in two on purpose. Per-player facts (are you the Avatar, how
 * many lives are left, what elements did you have first) live on BendingData,
 * where every check can reach them cheaply and copyOnDeath carries them through a
 * death. Facts about the WORLD (is the cycle running, which element it has
 * reached, who currently holds the title) live on AvatarState, held by the
 * overworld -- see ModAttachments.AVATAR_STATE.
 */
public class Avatar {

    /** Health below which the Avatar's last stand kicks in -- 3 hearts. */
    public static final float LOW_HEALTH = 6.0F;

    private static final int RESISTANCE_LEVEL = 1;   // Resistance II
    private static final int REGENERATION_LEVEL = 1; // Regeneration II
    private static final int GLOWING_LEVEL = 0;      // Glowing has no levels

    /**
     * How often the cycle looks again for someone to be the Avatar.
     *
     * The search skips past elements nobody can claim, so this only matters when the
     * server has NOBODY who could be the Avatar at all -- an empty server, or one
     * where nobody has chosen an element yet. That is not an error, it is simply
     * waiting, and retrying on a timer means the title lands on the first qualifying
     * player to log in rather than the cycle silently doing nothing because nobody
     * happened to be there the moment it started.
     */
    private static final int SEARCH_EVERY = 100; // 5 seconds

    private Avatar() {}

    // ---------------------------------------------------------------- state

    public static AvatarState state(MinecraftServer server) {
        return server.overworld().getData(ModAttachments.AVATAR_STATE);
    }

    /** The current Avatar, if they are online. Null when there is none, or they are not. */
    public static ServerPlayer currentAvatar(MinecraftServer server) {
        AvatarState state = state(server);
        if (!state.hasAvatar()) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(state.getAvatarId()));
        } catch (IllegalArgumentException malformed) {
            state.setAvatarId("");
            return null;
        }
    }

    // ------------------------------------------------------------- granting

    /**
     * Makes a player the Avatar: all four elements, three lives, and the title
     * taken off whoever held it before. There is only ever ONE Avatar.
     */
    public static void grant(MinecraftServer server, ServerPlayer player) {
        AvatarState state = state(server);

        // The old Avatar loses it first, and by UUID rather than by searching the
        // player list, so the title is taken back even from someone offline.
        revokeCurrent(server);

        BendingData data = player.getData(ModAttachments.BENDING_DATA);

        // Remember what they had, so losing the title gives back exactly that and
        // does not quietly delete an element they earned or were granted.
        data.setPreAvatarElements(new ArrayList<>(data.getUnlockedElements()));

        for (String element : AvatarState.CYCLE) {
            if (!containsIgnoreCase(data.getUnlockedElements(), element)) {
                data.getUnlockedElements().add(element);
            }
        }
        if (data.getActiveElement().isEmpty() && !data.getUnlockedElements().isEmpty()) {
            data.setActiveElement(data.getUnlockedElements().get(0));
        }

        data.setAvatar(true);
        data.setAvatarLives(BendingData.AVATAR_LIVES);
        player.setData(ModAttachments.BENDING_DATA, data);

        state.setAvatarId(player.getUUID().toString());

        sync(player, data);

        player.sendSystemMessage(Component.literal("You are the Avatar. All four elements are yours.")
                .withStyle(ChatFormatting.GOLD));
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(player.getGameProfile().getName() + " is the Avatar.")
                        .withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * Takes the title back from whoever the world says has it.
     *
     * Works off the tracked UUID rather than off the online player list, because an
     * Avatar who is logged out still holds the title -- the flag on their own data
     * is simply out of reach until they return. {@link #checkOnLogin} is the other
     * half of that: it strips the flag from anyone who comes back holding it
     * without being the tracked Avatar any more.
     */
    public static void revokeCurrent(MinecraftServer server) {
        AvatarState state = state(server);
        if (!state.hasAvatar()) return;

        ServerPlayer previous = currentAvatar(server);
        state.setAvatarId("");

        if (previous != null) strip(previous);
    }

    /**
     * Clears the Avatar flag from a player and gives back the elements they had
     * before. Does NOT touch the world state -- callers own that.
     */
    public static void strip(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING_DATA);
        if (!data.isAvatar()) return;

        List<String> before = new ArrayList<>(data.getPreAvatarElements());

        // A player who somehow became the Avatar with nothing recorded keeps their
        // main element rather than being left with no bending at all.
        if (before.isEmpty() && !data.getMainElement().isEmpty()) {
            before.add(data.getMainElement());
        }

        data.getUnlockedElements().clear();
        data.getUnlockedElements().addAll(before);
        data.setPreAvatarElements(new ArrayList<>());

        if (!containsIgnoreCase(data.getUnlockedElements(), data.getActiveElement())) {
            data.setActiveElement(data.getUnlockedElements().isEmpty()
                    ? "" : data.getUnlockedElements().get(0));
        }

        data.setAvatar(false);
        data.setAvatarLives(BendingData.AVATAR_LIVES);

        // The last stand goes with the title, whatever their health is.
        clearBuffs(player, data);

        player.setData(ModAttachments.BENDING_DATA, data);
        sync(player, data);

        player.sendSystemMessage(Component.literal("You are no longer the Avatar.")
                .withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- cycle

    /** Begins the cycle at earth and looks for its first Avatar straight away. */
    public static void startCycle(MinecraftServer server) {
        AvatarState state = state(server);
        state.setCycleRunning(true);
        state.setCycleIndex(0);

        revokeCurrent(server);
        findAvatar(server);
    }

    /** Ends the cycle and leaves nobody holding the title. */
    public static void stopCycle(MinecraftServer server) {
        AvatarState state = state(server);
        state.setCycleRunning(false);
        state.setCycleIndex(0);

        revokeCurrent(server);

        // Sweeps everyone online as well as the tracked Avatar. The two should be
        // the same player, but "remove all avatars" is what this command promises,
        // and a flag left on somebody by a mid-cycle crash should not survive it.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getData(ModAttachments.BENDING_DATA).isAvatar()) strip(player);
        }
    }

    /**
     * Picks a random Avatar from the players whose FIRST chosen element matches the
     * one the cycle has reached, SKIPPING past any element nobody online can claim.
     *
     * An empty element is not something to wait on: earth with no earthbender moves
     * straight to fire, fire to air, air to water. The loop runs at most one full
     * lap, and that bound is what makes it safe -- four advances come back round to
     * where it started, so a lap that finds nobody at all leaves the cycle exactly
     * as it was and simply waits for the next search.
     *
     * Returns false only when the whole server has nobody who could be the Avatar.
     */
    public static boolean findAvatar(MinecraftServer server) {
        return findAvatar(server, null);
    }

    /**
     * As above, but preferring anyone other than {@code avoid} -- the Avatar who has
     * just fallen.
     *
     * That only ever matters when the lap comes all the way back round to their own
     * element, which happens when nobody online bends anything else. The title
     * should pass ON where it can, so they are filtered out; but they are put back
     * in when they are the ONLY candidate, because the alternative is a cycle with
     * no Avatar and no way to ever get one. On a single-player world that is the
     * normal case, and it reads as the Avatar being reborn rather than as the cycle
     * quietly breaking.
     */
    public static boolean findAvatar(MinecraftServer server, ServerPlayer avoid) {
        AvatarState state = state(server);
        if (!state.isCycleRunning() || state.hasAvatar()) return false;

        for (int attempt = 0; attempt < AvatarState.CYCLE.length; attempt++) {
            List<ServerPlayer> candidates = candidatesFor(server, state.getCycleElement());

            if (avoid != null && candidates.size() > 1) candidates.remove(avoid);

            if (!candidates.isEmpty()) {
                grant(server, candidates.get(server.overworld().random.nextInt(candidates.size())));
                return true;
            }

            state.advanceCycle();
        }

        return false;
    }

    /** Everyone online whose FIRST chosen element is the one given. */
    private static List<ServerPlayer> candidatesFor(MinecraftServer server, String element) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);
            if (element.equalsIgnoreCase(data.getMainElement())) candidates.add(player);
        }
        return candidates;
    }

    /**
     * Keeps looking while the cycle is running and nobody holds the title.
     *
     * Called from the server tick. Costs a list walk every five seconds and only
     * when there is actually no Avatar, so a running cycle with one in place does
     * no work at all.
     */
    public static void tickCycle(MinecraftServer server) {
        if (server.getTickCount() % SEARCH_EVERY != 0) return;

        AvatarState state = state(server);
        if (!state.isCycleRunning() || state.hasAvatar()) return;

        findAvatar(server);
    }

    // ----------------------------------------------------------------- life

    /**
     * One life spent. When the last one goes the title moves on -- to the next
     * element if the cycle is running, or simply away if the Avatar was named by
     * hand.
     *
     * Runs on the DYING player's data, which is right: LivingDeathEvent fires
     * before PlayerEvent.Clone, so the decrement is carried onto the new body with
     * everything else.
     */
    public static void onDeath(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING_DATA);
        if (!data.isAvatar()) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        // The last stand does not survive the death that ended it. Clearing the
        // flag matters more than the effects, which a respawn drops anyway.
        data.setAvatarBuffed(false);

        int left = data.getAvatarLives() - 1;

        if (left > 0) {
            data.setAvatarLives(left);
            player.setData(ModAttachments.BENDING_DATA, data);
            sync(player, data);

            player.sendSystemMessage(Component.literal(
                    "The Avatar falls. " + left + (left == 1 ? " life" : " lives") + " left.")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }

        // Out of lives.
        player.sendSystemMessage(Component.literal("The Avatar has fallen for the last time.")
                .withStyle(ChatFormatting.RED));

        AvatarState state = state(server);
        state.setAvatarId("");
        strip(player);

        if (state.isCycleRunning()) {
            state.advanceCycle();

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("The Avatar has passed on. The cycle turns.")
                            .withStyle(ChatFormatting.GOLD), false);

            // The new element is deliberately NOT named here. The search skips past
            // any element nobody online can claim, so where the cycle lands is not
            // necessarily the next one along -- grant() announces whoever it settles
            // on, and only a search that finds nobody at all needs a word of its own.
            if (!findAvatar(server, player)) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("Nobody online can take up the cycle. It rests on "
                                + state.getCycleElement() + ".").withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    /**
     * The Avatar's last stand: Resistance and Regeneration below three hearts, gone
     * again the moment they climb back above it.
     *
     * Regeneration is only re-applied once the previous instance has EXPIRED, not
     * topped up every tick. Re-adding replaces the instance and resets the internal
     * counter it heals on, so a per-tick refresh gives a permanent icon that never
     * heals a single point -- the same trap Water heal works around, for the same
     * reason.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        if (!data.isAvatar()) {
            // Nothing to take off if it was never put on, and this runs every tick
            // for every player -- so the common case costs one boolean read.
            if (data.isAvatarBuffed()) clearBuffs(player, data);
            return;
        }

        if (player.getHealth() < LOW_HEALTH && player.isAlive()) {
            data.setAvatarBuffed(true);

            if (player.getEffect(MobEffects.REGENERATION) == null) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                        regenDuration(REGENERATION_LEVEL), REGENERATION_LEVEL, false, true, true));
            }

            // Resistance is not counter-driven, so it can simply be held topped up.
            // It still is not re-applied every tick: each addEffect is a packet, and
            // twenty a second for as long as an Avatar is hurt is pure noise.
            MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance == null || resistance.getDuration() < 20) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                        60, RESISTANCE_LEVEL, false, true, true));
            }

            // Glowing, so an Avatar in their last stand is unmistakable — to
            // everyone else as much as to themselves. Topped up the same way, and
            // for the same reason: this is not a counter-driven effect, but
            // re-applying it twenty times a second would still be twenty packets.
            MobEffectInstance glowing = player.getEffect(MobEffects.GLOWING);
            if (glowing == null || glowing.getDuration() < 20) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                        60, GLOWING_LEVEL, false, true, true));
            }
        } else if (data.isAvatarBuffed()) {
            clearBuffs(player, data);
        }
    }

    /**
     * Regeneration heals on ticks where its remaining duration divides by
     * {@code 50 >> amplifier} -- the interval halves at each level -- so the
     * duration has to be derived from the amplifier rather than fixed, or a
     * stronger regen would claim to heal faster without doing so.
     */
    private static int regenDuration(int amplifier) {
        return Math.max(1, 50 >> amplifier);
    }

    /**
     * Takes the last stand back off, and only if we were the ones who put it on.
     *
     * The amplifier is checked as well as the flag so a potion the player drank
     * themselves is left alone rather than being stripped by something that did not
     * grant it.
     */
    private static void clearBuffs(ServerPlayer player, BendingData data) {
        data.setAvatarBuffed(false);

        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        if (regen != null && regen.getAmplifier() == REGENERATION_LEVEL) {
            player.removeEffect(MobEffects.REGENERATION);
        }

        MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null && resistance.getAmplifier() == RESISTANCE_LEVEL) {
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }

        MobEffectInstance glowing = player.getEffect(MobEffects.GLOWING);
        if (glowing != null && glowing.getAmplifier() == GLOWING_LEVEL) {
            player.removeEffect(MobEffects.GLOWING);
        }
    }

    // ----------------------------------------------------------------- sync

    /**
     * Corrects a player who logs in still flagged as the Avatar when the world says
     * somebody else is -- or that nobody is.
     *
     * This is what makes revoking an OFFLINE Avatar work: the command clears the
     * tracked UUID, which is all it can reach, and the flag on the player is put
     * right the moment they come back.
     */
    public static void checkOnLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        BendingData data = player.getData(ModAttachments.BENDING_DATA);
        if (data.isAvatar() && !player.getUUID().toString().equals(state(server).getAvatarId())) {
            strip(player);
            return;
        }

        sync(player, data);
    }

    /**
     * Pushes both halves of an Avatar change to the client: the HUD's lives counter
     * and the element list, which the title adds to and takes away from.
     */
    public static void sync(ServerPlayer player, BendingData data) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SyncAvatarPacket(data.isAvatar(), data.getAvatarLives()));

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SyncBendingDataPacket(
                        data.getMainElement(),
                        data.getActiveElement(),
                        data.getUnlockedElements(),
                        data.hasChosenElement(),
                        data.getUnlockedAbilities(),
                        data.getEquippedAbilities()));
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (value == null || value.isEmpty()) return false;
        for (String entry : list) {
            if (value.equalsIgnoreCase(entry)) return true;
        }
        return false;
    }
}
