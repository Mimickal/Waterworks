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
import mimickal.minecraft.util.ListUtil;
import mimickal.minecraft.util.TickGuard;
import mimickal.minecraft.waterworks.Config;
import mimickal.minecraft.waterworks.eva.EvaData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public class Evaporation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, TickGuard.Config> TICK_GUARDS = new HashMap<>();

    /**
     * {@link TickEvent.WorldTickEvent} handler that evaporates water when it's clear.
     * Evaporation intensity scales with sun intensity.
     * In other words, the closer to noon it is, the faster water evaporates.
     * The minimum time-of-day scaling is applied throughout the night.
     * <p>
     * Water only evaporates when:
     * <li>It's not raining.</li>
     * <li>The water is visible to the sky.</li>
     */
    @SubscribeEvent
    public static void evaporateWhenClear(TickEvent.WorldTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (!Config.evaporationEnabled.get()) return;
        if (event.world.isRaining()) return;

        TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard.Config(Config.evaporationSmoothness));
        if (!TICK_GUARDS.get(event.world.dimension()).ready()) return;

        ServerLevel world = (ServerLevel) event.world;

        ChunkUtil.streamLoadedChunks(world)
            .map(ChunkHolder::getTickingChunk)
            .filter(Objects::nonNull)
            .filter(chunk -> Chance.percent(Chance.scaleWithSmoothness(
                Config.evaporationIntensity.get(), Config.evaporationSmoothness.get()
            )))
            .filter(chunk -> Chance.decimal(timeOfDayScale(world)))
            .map(chunk -> findSourceInChunk(world, chunk))
            .filter(Objects::nonNull)
            .filter(chunkBlockPos -> Chance.decimal(getEvaporationChance(world, chunkBlockPos)))
            .forEach(waterPos -> evaporateAtPosition(world, waterPos));
    }

    /**
     * Searches the surface of the given chunk for a water source block.
     * <p>
     * This returns {@code null} if no source is found.
     */
    @Nullable
    private static BlockPos findSourceInChunk(ServerLevel world, LevelChunk chunk) {
        return ChunkUtil.blocksInChunkArea(chunk)
            .collect(ListUtil.toShuffledList()) // Shuffle so we don't drill straight down in large bodies of water
            .stream()
            .map(blockPos -> world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos))
            .map(BlockPos::below)
            .filter(blockPos -> world.getFluidState(blockPos).isSource())
            .filter(blockPos -> world.getBlockState(blockPos).is(Blocks.WATER))
            .findFirst()
            .orElse(null);
    }

    /**
     * Evaporate water at the given position (and track it in world data).
     * <p>
     * This method handles evaporating partial water blocks, if using a water physics mod that supports it.
     */
    private static void evaporateAtPosition(ServerLevel world, BlockPos pos) {
        LOGGER.debug("Evaporating at {}", pos);
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        EvaData.get(world).changeHumidity(pos, 1000);
    }

    /**
     * Returns a scalar that is at its max when the sun is highest, and minimum when the sun disappears.
     * It remains at that minimum throughout the night.
     */
    private static double timeOfDayScale(ServerLevel world) {
        // One hour = 1000 "units". 0 is 6AM, 1000 is 7AM, 6000 is "noon", 18000 is "midnight" etc...
        // These are the same units used in the "/time set X" command.
        // This number continues counting up the next day, so 24000 is 6AM the next day.
        // The sun first appears on the horizon at 5AM (23000). Note, DOES NOT map to "day" ("day" is 7AM, 1000).
        // The sun is highest at 12 AM (6000), also mapped to keyword "noon".
        // The sun disappears under the horizon at 7PM (13000), also mapped to keyword "night".

        double min = 1 - Config.evaporationSunCoefficient.get();
        long tod = world.getLevelData().getDayTime();
        tod += 1000;  // Shift so sun appearance is 0 instead of 23000. This just makes the math easier.
        tod %= 24000; // Always deal with the 0 - 24000 range.

        // We pull this off with a piecewise function.
        if (min < 1 && 0 <= tod && tod <= 14000) {
            // During the day, time-of-day corresponds to the angle of the sun in the sky.
            // Normalize time-of-day to value between 0 and PI so sine can work its magic.
            return min + ((1 - min) * Math.sin(tod * Math.PI / 14000));
        } else {
            // During the night, just return the minimum.
            return min;
        }
    }

    /**
     * Returns the chance (as a {@link Double} 0.0 - 1.0) water should evaporate in the chunk this block is located in.
     * <p>
     * Vanilla biomes types have a `downfall` value that the game uses to determine its rain intensity relative to
     * other biome types. This value seems to loosely correlate with how "dry" a biome should be
     * (e.g. deserts have a low `downfall` value).
     * We factor this into the evaporation chance calculation so dryer biomes evaporate more frequently.
     */
    private static double getEvaporationChance(ServerLevel world, BlockPos pos) {
        return 1 - world.getBiome(pos).value().getDownfall();
    }
}
