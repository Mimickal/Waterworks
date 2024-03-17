package mimickal.minecraft.waterworks.eva.events;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.Chance;
import mimickal.minecraft.util.ChunkUtil;
import mimickal.minecraft.util.TickGuard;
import mimickal.minecraft.waterworks.eva.EvaData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

public class Rain {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, TickGuard.Random> TICK_GUARDS = new HashMap<>();

    private static final int RAIN_DELAY_MIN = 10 * 20; // 10 seconds
    private static final int RAIN_DELAY_MAX = 10 * 20; // 30 seconds

    // TODO what is wrong with me? Just expose this as a configurable value with a sane default.
    /**
     * There's not a good concrete value for the "max humidity" of a chunk, so we just make one up.
     * This allows us to express a chunk's humidity as a percentage.
     */
    private static final double MAX_HUMIDITY = 16 * 16; // Surface area of a chunk

    /**
     * {@link TickEvent.WorldTickEvent} that determines when rainstorms start and controls how long they last.
     * <p>
     * Rain probability is based on the average relative humidity of all loaded chunks.
     * "Humidity" is the amount of water evaporated in a chunk. We ultimately express this as a percentage of a chunk's
     * max humidity (See {@link #MAX_HUMIDITY}).
     * The higher the relative humidity, the more likely rain is, and vice versa.
     * <p>
     * This does not disable or change any other vanilla rain mechanics.
     * However, if rain is stopped prematurely, it will likely restart the next time this event fires.
     */
    @SubscribeEvent
    public static void controlRain(TickEvent.WorldTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (!event.world.dimensionType().hasSkyLight()) return;

        TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard.Random(RAIN_DELAY_MIN, RAIN_DELAY_MAX));
        if (!TICK_GUARDS.get(event.world.dimension()).ready()) return;

        ServerLevel world = (ServerLevel) event.world;

        double avgHumidity = StreamSupport.stream(ChunkUtil.getLoadedChunks(world).spliterator(), false)
            .map(chunkHolder -> ChunkUtil.getRandomBlockInChunk(world, chunkHolder))
            .mapToDouble(chunkBlockPos -> EvaData.get(world).getHumidity(chunkBlockPos) / MAX_HUMIDITY)
            .average()
            .orElse(0);

        LOGGER.debug("Rain check in {} (humidity: {})", name(world), avgHumidity);

        if (world.isRaining()) {
            if (Chance.decimal(1 - avgHumidity)) {
                stopRaining(world);
            }
        } else {
            if (Chance.decimal(avgHumidity)) {
                startRaining(world);
            }
        }
    }

    /** Start raining indefinitely in the given world. */
    private static void startRaining(ServerLevel world) {
        LOGGER.debug("Rain start in {}", name(world));
        world.setWeatherParameters(
            0 /* Clear time */,
            Integer.MAX_VALUE/* Rain and thunder time */,
            true /* Set is raining */,
            // TODO for now we just never thunder
            false /* Set is thundering */
        );
    }

    /** Stop raining in the given world. */
    private static void stopRaining(ServerLevel world) {
        LOGGER.debug("Rain stop in {}", name(world));
        world.setWeatherParameters(0, 0, false, false);
    }

    /** Convert a {@link ServerLevel} to a log-friendly name. */
    private static String name(ServerLevel world) {
        return world.dimension().location().toString();
    }
}
