package io.github.niilo411.elementalpaths.paragon;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.ModAttachments;
import io.github.niilo411.elementalpaths.network.SyncParagonPacket;
import io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket;
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
 * Everything the Paragon is: granting and revoking the title, the three lives, the
 * cycle that hands it on through earth -> fire -> air -> water, and the last-stand
 * buffs that come up when a Paragon is nearly dead.
 *
 * The state is split in two on purpose. Per-player facts (are you the Paragon, how
 * many lives are left, what elements did you have first) live on ChannelingData,
 * where every check can reach them cheaply and copyOnDeath carries them through a
 * death. Facts about the WORLD (is the cycle running, which element it has
 * reached, who currently holds the title) live on ParagonState, held by the
 * overworld -- see ModAttachments.PARAGON_STATE.
 */
public class Paragon {

    /** Health below which the Paragon's last stand kicks in -- 3 hearts. */
    public static final float LOW_HEALTH = 6.0F;

    private static final int RESISTANCE_LEVEL = 1;   // Resistance II
    private static final int REGENERATION_LEVEL = 1; // Regeneration II
    private static final int GLOWING_LEVEL = 0;      // Glowing has no levels

    /**
     * How often the cycle looks again for someone to be the Paragon.
     *
     * The search skips past elements nobody can claim, so this only matters when the
     * server has NOBODY who could be the Paragon at all -- an empty server, or one
     * where nobody has chosen an element yet. That is not an error, it is simply
     * waiting, and retrying on a timer means the title lands on the first qualifying
     * player to log in rather than the cycle silently doing nothing because nobody
     * happened to be there the moment it started.
     */
    private static final int SEARCH_EVERY = 100; // 5 seconds

    private Paragon() {}

    // ---------------------------------------------------------------- state

    public static ParagonState state(MinecraftServer server) {
        return server.overworld().getData(ModAttachments.PARAGON_STATE);
    }

    /** The current Paragon, if they are online. Null when there is none, or they are not. */
    public static ServerPlayer currentParagon(MinecraftServer server) {
        ParagonState state = state(server);
        if (!state.hasParagon()) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(state.getParagonId()));
        } catch (IllegalArgumentException malformed) {
            state.setParagonId("");
            return null;
        }
    }

    // ------------------------------------------------------------- granting

    /**
     * Makes a player the Paragon: all four elements, three lives, and the title
     * taken off whoever held it before. There is only ever ONE Paragon.
     */
    public static boolean grant(MinecraftServer server, ServerPlayer player) {
        // A NON-CHANNELER IS NEVER THE PARAGON, and that has to be refused here rather than
        // left to the cycle. The cycle only ever looks for the four channeling arts, so it
        // skips them for free; being NAMED Paragon by command is the route that reaches
        // this method directly, and it is the one that had to be closed. Granting it
        // would also destroy the choice outright, since becoming the Paragon hands over
        // all four elements — the exact opposite of what they picked.
        //
        // Asks whether they CHANNEL anything rather than what they once chose, so somebody
        // who picked no channeling and was later given a real element can hold the title like
        // anyone else, and a channeler who was given the no-channeling path is unaffected.
        ChannelingData chosen = player.getData(ModAttachments.CHANNELING_DATA);
        if (io.github.niilo411.elementalpaths.abilities.nochanneling.NoChanneling.is(chosen)) {
            return false;
        }

        ParagonState state = state(server);

        // The old Paragon loses it first, and by UUID rather than by searching the
        // player list, so the title is taken back even from someone offline.
        revokeCurrent(server);

        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

        // Remember what they had, so losing the title gives back exactly that and
        // does not quietly delete an element they earned or were granted.
        data.setPreParagonElements(new ArrayList<>(data.getUnlockedElements()));

        for (String element : ParagonState.CYCLE) {
            if (!containsIgnoreCase(data.getUnlockedElements(), element)) {
                data.getUnlockedElements().add(element);
            }
        }
        if (data.getActiveElement().isEmpty() && !data.getUnlockedElements().isEmpty()) {
            data.setActiveElement(data.getUnlockedElements().get(0));
        }

        data.setParagon(true);
        data.setParagonLives(ChannelingData.PARAGON_LIVES);
        player.setData(ModAttachments.CHANNELING_DATA, data);

        state.setParagonId(player.getUUID().toString());

        sync(player, data);

        player.sendSystemMessage(Component.literal("You are the Paragon. All four elements are yours.")
                .withStyle(ChatFormatting.GOLD));
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(player.getGameProfile().getName() + " is the Paragon.")
                        .withStyle(ChatFormatting.GOLD), false);

        return true;
    }

    /**
     * Takes the title back from whoever the world says has it.
     *
     * Works off the tracked UUID rather than off the online player list, because an
     * Paragon who is logged out still holds the title -- the flag on their own data
     * is simply out of reach until they return. {@link #checkOnLogin} is the other
     * half of that: it strips the flag from anyone who comes back holding it
     * without being the tracked Paragon any more.
     */
    public static void revokeCurrent(MinecraftServer server) {
        ParagonState state = state(server);
        if (!state.hasParagon()) return;

        ServerPlayer previous = currentParagon(server);
        state.setParagonId("");

        if (previous != null) strip(previous);
    }

    /**
     * Clears the Paragon flag from a player and gives back the elements they had
     * before. Does NOT touch the world state -- callers own that.
     */
    public static void strip(ServerPlayer player) {
        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);
        if (!data.isParagon()) return;

        List<String> before = new ArrayList<>(data.getPreParagonElements());

        // A player who somehow became the Paragon with nothing recorded keeps their
        // main element rather than being left with no channeling at all.
        if (before.isEmpty() && !data.getMainElement().isEmpty()) {
            before.add(data.getMainElement());
        }

        data.getUnlockedElements().clear();
        data.getUnlockedElements().addAll(before);
        data.setPreParagonElements(new ArrayList<>());

        if (!containsIgnoreCase(data.getUnlockedElements(), data.getActiveElement())) {
            data.setActiveElement(data.getUnlockedElements().isEmpty()
                    ? "" : data.getUnlockedElements().get(0));
        }

        data.setParagon(false);
        data.setParagonLives(ChannelingData.PARAGON_LIVES);

        // The last stand goes with the title, whatever their health is.
        clearBuffs(player, data);

        player.setData(ModAttachments.CHANNELING_DATA, data);
        sync(player, data);

        player.sendSystemMessage(Component.literal("You are no longer the Paragon.")
                .withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- cycle

    /** Begins the cycle at earth and looks for its first Paragon straight away. */
    public static void startCycle(MinecraftServer server) {
        ParagonState state = state(server);
        state.setCycleRunning(true);
        state.setCycleIndex(0);

        revokeCurrent(server);
        findParagon(server);
    }

    /** Ends the cycle and leaves nobody holding the title. */
    public static void stopCycle(MinecraftServer server) {
        ParagonState state = state(server);
        state.setCycleRunning(false);
        state.setCycleIndex(0);

        revokeCurrent(server);

        // Sweeps everyone online as well as the tracked Paragon. The two should be
        // the same player, but "remove all paragons" is what this command promises,
        // and a flag left on somebody by a mid-cycle crash should not survive it.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getData(ModAttachments.CHANNELING_DATA).isParagon()) strip(player);
        }
    }

    /**
     * Picks a random Paragon from the players whose FIRST chosen element matches the
     * one the cycle has reached, SKIPPING past any element nobody online can claim.
     *
     * An empty element is not something to wait on: earth with no earth channeler moves
     * straight to fire, fire to air, air to water. The loop runs at most one full
     * lap, and that bound is what makes it safe -- four advances come back round to
     * where it started, so a lap that finds nobody at all leaves the cycle exactly
     * as it was and simply waits for the next search.
     *
     * Returns false only when the whole server has nobody who could be the Paragon.
     */
    public static boolean findParagon(MinecraftServer server) {
        return findParagon(server, null);
    }

    /**
     * As above, but preferring anyone other than {@code avoid} -- the Paragon who has
     * just fallen.
     *
     * That only ever matters when the lap comes all the way back round to their own
     * element, which happens when nobody online channels anything else. The title
     * should pass ON where it can, so they are filtered out; but they are put back
     * in when they are the ONLY candidate, because the alternative is a cycle with
     * no Paragon and no way to ever get one. On a single-player world that is the
     * normal case, and it reads as the Paragon being reborn rather than as the cycle
     * quietly breaking.
     */
    public static boolean findParagon(MinecraftServer server, ServerPlayer avoid) {
        ParagonState state = state(server);
        if (!state.isCycleRunning() || state.hasParagon()) return false;

        for (int attempt = 0; attempt < ParagonState.CYCLE.length; attempt++) {
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

    /**
     * Everyone online whose FIRST chosen element is the one given.
     *
     * THE CYCLE CAN NEVER REACH NO CHANNELING. Its list is the four channeling arts
     * ({@link ParagonState#CYCLE}) and nothing adds to it, so a non-channeler is already
     * unreachable by the element match alone. The second test is belt and braces: it
     * states the rule where a reader looking for it would look, and it means a candidate
     * this method returns is always one {@link #grant} will accept — which matters because
     * {@link #findParagon} treats a returned candidate as the search being over, and a
     * refusal it did not expect would leave the cycle believing it had found somebody.
     */
    private static List<ServerPlayer> candidatesFor(MinecraftServer server, String element) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

            if (io.github.niilo411.elementalpaths.abilities.nochanneling.NoChanneling.is(data)) continue;
            if (element.equalsIgnoreCase(data.getMainElement())) candidates.add(player);
        }
        return candidates;
    }

    /**
     * Keeps looking while the cycle is running and nobody holds the title.
     *
     * Called from the server tick. Costs a list walk every five seconds and only
     * when there is actually no Paragon, so a running cycle with one in place does
     * no work at all.
     */
    public static void tickCycle(MinecraftServer server) {
        if (server.getTickCount() % SEARCH_EVERY != 0) return;

        ParagonState state = state(server);
        if (!state.isCycleRunning() || state.hasParagon()) return;

        findParagon(server);
    }

    // ----------------------------------------------------------------- life

    /**
     * One life spent. When the last one goes the title moves on -- to the next
     * element if the cycle is running, or simply away if the Paragon was named by
     * hand.
     *
     * Runs on the DYING player's data, which is right: LivingDeathEvent fires
     * before PlayerEvent.Clone, so the decrement is carried onto the new body with
     * everything else.
     */
    public static void onDeath(ServerPlayer player) {
        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);
        if (!data.isParagon()) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        // The last stand does not survive the death that ended it. Clearing the
        // flag matters more than the effects, which a respawn drops anyway.
        data.setParagonBuffed(false);

        int left = data.getParagonLives() - 1;

        if (left > 0) {
            data.setParagonLives(left);
            player.setData(ModAttachments.CHANNELING_DATA, data);
            sync(player, data);

            player.sendSystemMessage(Component.literal(
                    "The Paragon falls. " + left + (left == 1 ? " life" : " lives") + " left.")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }

        // Out of lives.
        player.sendSystemMessage(Component.literal("The Paragon has fallen for the last time.")
                .withStyle(ChatFormatting.RED));

        ParagonState state = state(server);
        state.setParagonId("");
        strip(player);

        if (state.isCycleRunning()) {
            state.advanceCycle();

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("The Paragon has passed on. The cycle turns.")
                            .withStyle(ChatFormatting.GOLD), false);

            // The new element is deliberately NOT named here. The search skips past
            // any element nobody online can claim, so where the cycle lands is not
            // necessarily the next one along -- grant() announces whoever it settles
            // on, and only a search that finds nobody at all needs a word of its own.
            if (!findParagon(server, player)) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("Nobody online can take up the cycle. It rests on "
                                + state.getCycleElement() + ".").withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    /**
     * The Paragon's last stand: Resistance and Regeneration below three hearts, gone
     * again the moment they climb back above it.
     *
     * Regeneration is only re-applied once the previous instance has EXPIRED, not
     * topped up every tick. Re-adding replaces the instance and resets the internal
     * counter it heals on, so a per-tick refresh gives a permanent icon that never
     * heals a single point -- the same trap Water heal works around, for the same
     * reason.
     */
    public static void tick(ServerPlayer player, ChannelingData data) {
        if (!data.isParagon()) {
            // Nothing to take off if it was never put on, and this runs every tick
            // for every player -- so the common case costs one boolean read.
            if (data.isParagonBuffed()) clearBuffs(player, data);
            return;
        }

        if (player.getHealth() < LOW_HEALTH && player.isAlive()) {
            data.setParagonBuffed(true);

            if (player.getEffect(MobEffects.REGENERATION) == null) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                        regenDuration(REGENERATION_LEVEL), REGENERATION_LEVEL, false, true, true));
            }

            // Resistance is not counter-driven, so it can simply be held topped up.
            // It still is not re-applied every tick: each addEffect is a packet, and
            // twenty a second for as long as a Paragon is hurt is pure noise.
            MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance == null || resistance.getDuration() < 20) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                        60, RESISTANCE_LEVEL, false, true, true));
            }

            // Glowing, so a Paragon in their last stand is unmistakable — to
            // everyone else as much as to themselves. Topped up the same way, and
            // for the same reason: this is not a counter-driven effect, but
            // re-applying it twenty times a second would still be twenty packets.
            MobEffectInstance glowing = player.getEffect(MobEffects.GLOWING);
            if (glowing == null || glowing.getDuration() < 20) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                        60, GLOWING_LEVEL, false, true, true));
            }
        } else if (data.isParagonBuffed()) {
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
    private static void clearBuffs(ServerPlayer player, ChannelingData data) {
        data.setParagonBuffed(false);

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
     * Corrects a player who logs in still flagged as the Paragon when the world says
     * somebody else is -- or that nobody is.
     *
     * This is what makes revoking an OFFLINE Paragon work: the command clears the
     * tracked UUID, which is all it can reach, and the flag on the player is put
     * right the moment they come back.
     */
    public static void checkOnLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);
        if (data.isParagon() && !player.getUUID().toString().equals(state(server).getParagonId())) {
            strip(player);
            return;
        }

        sync(player, data);
    }

    /**
     * Pushes both halves of a Paragon change to the client: the HUD's lives counter
     * and the element list, which the title adds to and takes away from.
     */
    public static void sync(ServerPlayer player, ChannelingData data) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SyncParagonPacket(data.isParagon(), data.getParagonLives()));

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SyncChannelingDataPacket(
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
