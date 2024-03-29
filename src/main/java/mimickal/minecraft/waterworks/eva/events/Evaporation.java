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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

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
        DistanceManager distanceManager = world.getChunkSource().chunkMap.getDistanceManager();

        StreamSupport.stream(ChunkUtil.getLoadedChunks(world).spliterator(), false)
            .filter(chunkHolder -> distanceManager.inBlockTickingRange(chunkHolder.getPos().toLong()))
            .filter(chunkHolder -> Chance.percent(Chance.scaleWithSmoothness(
                Config.evaporationIntensity.get(), Config.evaporationSmoothness.get()
            )))
            .filter(chunkHolder -> Chance.decimal(timeOfDayScale(world)))
            .map(chunkHolder -> ChunkUtil.getRandomBlockInChunk(world, chunkHolder))
            .filter(chunkBlockPos -> Chance.percent(getEvaporationChance(world, chunkBlockPos)))
            .map(chunkBlockPos -> world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chunkBlockPos))
            .map(BlockPos::below)
            .forEach(maybeWaterPos -> evaporateAtPosition(world, maybeWaterPos));
    }

    /**
     * Evaporate water at the given position (and track it in world data).
     * <p>
     * This method handles evaporating partial water blocks, if using a water physics mod that supports it.
     */
    private static void evaporateAtPosition(ServerLevel world, BlockPos pos) {
        BlockPos evapPos = null;

        if (world.getFluidState(pos).getType() == Fluids.WATER) {
            evapPos = pos;
        }

        if (world.getFluidState(pos).getType() == Fluids.FLOWING_WATER) {
            evapPos = findNearestSource(world, pos);
        }

        if (evapPos != null) {
            LOGGER.debug("Evaporating at {}" + (evapPos.equals(pos) ? "" : " (searched from {})"), evapPos, pos);
            world.setBlockAndUpdate(evapPos, Blocks.AIR.defaultBlockState());
            EvaData.get(world).changeHumidity(evapPos, 1000);
        }
    }

    /**
     * Given a {@link BlockPos} containing flowing fluid, searches for the location of the nearest source of that fluid.
     * <p>
     * This can return `null` if the starting position isn't a fluid, or we can't find a source within the step limit.
     */
    @Nullable
    private static BlockPos findNearestSource(ServerLevel world, BlockPos pos) {
        // Starting block wasn't water. This is going nowhere fast, so bail out early.
        if (world.getFluidState(pos).isEmpty()) {
            return null;
        }

        int maxSteps = Config.evaporationTenacity.get();
        int step = 0;
        BlockPos curPos = pos;
        BlockPos lastKnownGoodPos = pos;

        // This algorithm assumes that water always flows away from its source.
        // We follow the flow "backwards" until we find a source.
        // We also maintain one position of history so that if we make a wrong move, we can go back and try again.
        while (step < maxSteps && !world.getFluidState(curPos).isSource()) {
            Vec3 flow = world.getFluidState(curPos).getFlow(world, curPos);

            if (Vec3.ZERO.equals(flow)) {
                // TODO this is better than it was before, but still frequently hits the max step count.

                // We either have water that isn't moving, or a block that isn't water.
                if (world.getFluidState(curPos).getAmount() > 0) {
                    // We're in still, non-source water. There should be something useful adjacent to us.
                    if (DirHelper.hasNext()) {
                        curPos = curPos.relative(DirHelper.next());
                    } else {
                        LOGGER.debug("Lost the water at {} while searching", curPos);
                        break;
                    }
                } else {
                    // We're not in water at all
                    curPos = lastKnownGoodPos;
                }
            } else {
                // Continue searching in the opposite direction of the flow.
                DirHelper.reset();
                lastKnownGoodPos = curPos;
                curPos = new BlockPos(
                    curPos.getX() + (flow.x == 0 ? 0 : (flow.x < 0 ? 1 : -1)),
                    curPos.getY() + (flow.y == 0 ? 0 : (flow.y < 0 ? 1 : -1)),
                    curPos.getZ() + (flow.z == 0 ? 0 : (flow.z < 0 ? 1 : -1))
                );
            }

            step++;
        }

        LOGGER.debug("Searched for {} in {} steps", curPos, step);

        return world.getFluidState(curPos).isSource() ? curPos : null;
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
     * Returns the percent chance water should evaporate in the chunk this block is located in.
     * <p>
     * Vanilla biomes types have a `downfall` value that the game uses to determine its rain intensity relative to
     * other biome types. This value seems to loosely correlate with how "dry" a biome should be
     * (e.g. deserts have a low `downfall` value).
     * We factor this into the evaporation chance calculation so dryer biomes evaporate more frequently.
     */
    private static double getEvaporationChance(ServerLevel world, BlockPos pos) {
        return 1 - world.getBiome(pos).value().getDownfall();
    }

    /** Helps us find the next direction to search when following water flow doesn't work. */
    private static class DirHelper {
        private static final List<Direction> directions = Arrays.asList(
            Direction.UP,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
        );

        private static int curIndex;

        private static void reset() {
            curIndex = 0;
        }

        private static boolean hasNext() {
            return curIndex < directions.size();
        }

        @Nullable
        private static Direction next() {
            if (!hasNext()) {
                return null;
            }

            Direction nextDir = directions.get(curIndex);
            curIndex++;
            return nextDir;
        }
    }
}
