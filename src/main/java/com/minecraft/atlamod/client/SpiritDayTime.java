package com.minecraft.atlamod.client;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.spirit.SpiritBiomes;
import com.minecraft.atlamod.spirit.SpiritWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Holds the Spirit World at noon, except over the wasteland, where it is midnight.
 *
 * TIME IS A PROPERTY OF THE LEVEL, not of a biome, exactly as the skybox is — so this is
 * the same problem {@link SpiritSkyEffects} solves, and it needed a different answer.
 * The sky TYPE is chosen by an overridable getter, so that one could simply be answered
 * per biome. The time is read straight out of the level, and there is no getter to
 * override.
 *
 * What makes it work is that {@code DimensionType.timeOfDay} is
 * {@code fixedTime.orElse(dayTime)}. Take the dimension's {@code fixed_time} away — which
 * data/atlamod/dimension_type/spirit_world.json now does — and the level's own day time
 * decides the sky again, and the CLIENT's copy of that is something we are allowed to
 * set. So the time is simply written every client tick to whatever the biome underfoot
 * calls for.
 *
 * The server never sees any of this and does not need to. Nothing in the Spirit World
 * depends on the time of day: its mobs are spawned by our own spawner rather than by
 * vanilla's light rules, and there are no beds, crops or phantoms to care.
 *
 * WRITTEN EVERY TICK rather than once, because the server sends the real time over
 * periodically and would otherwise win. Twenty times a second is far more often than it
 * needs, and is still only a field assignment.
 */
@EventBusSubscriber(modid = Atlamod.MODID, value = Dist.CLIENT)
public final class SpiritDayTime {

    /** Noon. What every island but the wasteland sits under. */
    private static final long NOON = 6000L;

    /** Midnight, for the wasteland — a dead black island deserves a dead black sky. */
    private static final long MIDNIGHT = 18000L;

    private SpiritDayTime() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        hold(Minecraft.getInstance().level);
    }

    /**
     * Pins the time to whatever the biome underfoot calls for.
     *
     * CALLED FROM THE RENDER PATH AS WELL AS THE TICK, and it has to be. The server
     * broadcasts the real time once a second, which lands between our ticks and drags the
     * sky back to whatever hour the world actually thinks it is — for the rest of that
     * tick, every frame drawn shows the wrong sky. Once a second, for a few frames, reads
     * exactly as a flash between day and night.
     *
     * Setting it on the tick alone can never close that window, because the packet
     * arrives inside it. Setting it immediately before the sky is drawn does, because
     * nothing runs between the two. The tick call stays because lighting is recomputed
     * per tick rather than per frame, and would otherwise flicker in its own right.
     *
     * Cheap enough to run every frame: one biome lookup and a field write.
     */
    public static void hold(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();

        if (level == null || minecraft.player == null) return;
        if (!SpiritWorld.isSpiritWorld(level)) return;

        boolean wasteland = level.getBiome(minecraft.player.blockPosition())
                .is(SpiritBiomes.WASTELAND);

        level.setDayTime(wasteland ? MIDNIGHT : NOON);
    }
}
