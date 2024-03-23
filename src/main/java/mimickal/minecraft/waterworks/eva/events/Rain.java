/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.waterworks.eva.events;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.Chance;
import mimickal.minecraft.util.ChunkUtil;
import mimickal.minecraft.util.TickGuard;
import mimickal.minecraft.waterworks.Config;
import mimickal.minecraft.waterworks.eva.EvaData;
import net.minecraft.core.BlockPos;
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
    private static final int RAIN_DELAY_MAX = 30 * 20; // 30 seconds

    /**
     * This is the {@code a} in a best-fit regression for {@code y = x ^ a} using the following data points:<br>
     * {@code [x, y]: [0.0, 0.0], [0.5, 0.1], [0.75, 0.275], [1.0, 1.0]}
     * <p>
     * The curve this creates seems quite steep, but remember this chance is rolled every time this event runs.
     * For example, let's say we run this event every 30 seconds. If there's a 5% rain chance at 50% humidity,
     * it will take 20 events on average to roll a rainstorm, or about 10 minutes.
     * <p>
     * <a href="https://www.desmos.com/calculator">This online tool</a> was used to calculate the regression.
     */
    private static final double RAIN_CHANCE_EXPONENT = 4.23966;

    /**
     * {@link TickEvent.WorldTickEvent} that determines when rainstorms start and controls how long they last.
     * <p>
     * NOTE: both accumulation and evaporation need to be enabled for this event to take effect.<br>
     * If evaporation was disabled, rain would never start. If accumulation was disabled, rain would never stop.
     * <p>
     * Rain probability is based on the average relative humidity of all loaded chunks.
     * "Humidity" is the amount of water evaporated in a chunk. We ultimately express this as a fraction of a chunk's
     * max humidity (See {@link Config#rainChunkHumidityThreshold}).
     * The higher the relative humidity, the more likely rain is, and vice versa.
     * <p>
     * This does not disable or change any other vanilla rain mechanics.
     * However, if rain is stopped prematurely, it will likely restart the next time this event fires.
     */
    @SubscribeEvent
    public static void controlRain(TickEvent.WorldTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (!Config.accumulationEnabled.get()) return;
        if (!Config.evaporationEnabled.get()) return;
        if (!Config.rainModEnabled.get()) return;
        if (!event.world.dimensionType().hasSkyLight()) return;

        TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard.Random(RAIN_DELAY_MIN, RAIN_DELAY_MAX));
        if (!TICK_GUARDS.get(event.world.dimension()).ready()) return;

        ServerLevel world = (ServerLevel) event.world;

        double avgHumidity = StreamSupport.stream(ChunkUtil.getLoadedChunks(world).spliterator(), false)
            .map(chunkHolder -> ChunkUtil.getRandomBlockInChunk(world, chunkHolder))
            .mapToDouble(chunkBlockPos -> calcChunkHumidity(world, chunkBlockPos))
            .average()
            .orElse(0);

        LOGGER.debug("Rain check in {} (humidity: {})", name(world), avgHumidity);

        if (world.isRaining()) {
            // Subtracting from 1 here "mirrors" the probability on the Y-axis
            if (Chance.decimal(rainChanceFromHumidity(1 - avgHumidity))) {
                stopRaining(world);
            }
        } else {
            if (Chance.decimal(rainChanceFromHumidity(avgHumidity))) {
                startRaining(world);
            }
        }
    }

    /**
     * Calculates the humidity of the given chunk as a fraction of the configured "max humidity" threshold
     * (See {@link Config#rainChunkHumidityThreshold}).
     * <p>
     * Both of these values are measured in milli-buckets, so simple division gives us the desired value.
     * This resulting value can be above 1.0.
     * <p>
     * Having a {@link mimickal.minecraft.waterworks.ModBlocks#STATUE} in the chunk also slightly increases humidity.
     */
    private static double calcChunkHumidity(ServerLevel world, BlockPos blockPos) {
        double humidityMod = EvaData.get(world).getStatueCount(blockPos) > 0 ? 0.1 : 0;
        return (EvaData.get(world).getHumidity(blockPos) + humidityMod) / Config.rainChunkHumidityThreshold.get();
    }

    /**
     * Translates a percent humidity value to a percent rain chance (as a {@link Double} 0.0 - 1.0).
     * <p>
     * Relative humidity <i>is</i> a percentage already, but we can't just directly use it as the percent chance
     * to start or stop raining. If we did, humidity would stabilize around 50%, and the weather would flip-flop
     * between clear and rain every time we check.
     * <p>
     * Instead, we apply a curve so the rain chance "accelerates" proportionally with humidity.
     */
    private static double rainChanceFromHumidity(double humidity) {
        return Math.pow(humidity, RAIN_CHANCE_EXPONENT);
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
