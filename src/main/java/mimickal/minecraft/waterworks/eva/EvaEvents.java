package mimickal.minecraft.waterworks.eva;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.Chance;
import mimickal.minecraft.util.ChunkUtil;
import mimickal.minecraft.util.TickGuard;
import mimickal.minecraft.waterworks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

public class EvaEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, TickGuard> ACCUM_TICK_GUARDS = new HashMap<>();
    private static final Map<ResourceKey<Level>, TickGuard> EVAP_TICK_GUARDS = new HashMap<>();

    /**
     * WorldTickEvent handler that accumulates water when it's raining.
     *
     * Rain only accumulates when:
     * - It's raining
     * - The block is in a biome where it rains (e.g. not a desert).
     * - The block is visible to the sky.
     * - Rain can accumulate in the biome
     * - the selected block is not on the accumulation blacklist.
     */
    @SubscribeEvent
    public static void accumulateWhenRaining(TickEvent.WorldTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (!Config.accumulationEnabled.get()) return;
        if (!event.world.isRaining()) return;

        ACCUM_TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard(Config.accumulationSmoothness));
        if (!ACCUM_TICK_GUARDS.get(event.world.dimension()).ready()) return;

        ServerLevel world = (ServerLevel) event.world;
        DistanceManager distanceManager = world.getChunkSource().chunkMap.getDistanceManager();

        StreamSupport.stream(ChunkUtil.getLoadedChunks(world).spliterator(), false)
            .filter(chunkHolder -> distanceManager.inBlockTickingRange(chunkHolder.getPos().toLong()))
            .filter(chunkHolder -> Chance.percent(Config.accumulationIntensity.get()))
            .map(chunkHolder -> ChunkUtil.getRandomBlockInChunk(world, chunkHolder))
            .filter(chunkBlockPos -> world.getBiome(chunkBlockPos).value().getPrecipitation() == Biome.Precipitation.RAIN)
            .filter(chunkBlockPos -> Chance.percent(getAccumulationChance(world, chunkBlockPos)))
            .map(chunkBlockPos -> world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chunkBlockPos))
            .forEach(waterPos -> accumulateAtPosition(world, waterPos));
    }

    /**
     * WorldTickEvent handler that evaporates water when it's clear.
     * Evaporation intensity scales with sun intensity.
     * In other words, the closer to noon it is, the faster water evaporates.
     * The minimum time-of-day scaling is applied throughout the night.
     *
     * Water only evaporates when:
     * - It's not raining.
     * - The water is visible to the sky.
     */
    @SubscribeEvent
    public static void evaporateWhenClear(TickEvent.WorldTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (!Config.evaporationEnabled.get()) return;
        if (event.world.isRaining()) return;

        EVAP_TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard(Config.evaporationSmoothness));
        if (!EVAP_TICK_GUARDS.get(event.world.dimension()).ready()) return;

        ServerLevel world = (ServerLevel) event.world;
        DistanceManager distanceManager = world.getChunkSource().chunkMap.getDistanceManager();

        StreamSupport.stream(ChunkUtil.getLoadedChunks(world).spliterator(), false)
            .filter(chunkHolder -> distanceManager.inBlockTickingRange(chunkHolder.getPos().toLong()))
            .filter(chunkHolder -> Chance.percent(Config.evaporationIntensity.get()))
            .map(chunkHolder -> ChunkUtil.getRandomBlockInChunk(world, chunkHolder))
            .filter(chunkBlockPos -> Chance.percent(getEvaporationChance(world, chunkBlockPos)))
            .map(chunkBlockPos -> world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chunkBlockPos))
            .map(BlockPos::below)
            .forEach(maybeWaterPos -> evaporateAtPosition(world, maybeWaterPos));
    }

    /**
     * Accumulate water at the given position (and track it in world data).
     *
     * This method handles placing partial water blocks, if using a water physics mod that supports it.
     */
    private static void accumulateAtPosition(ServerLevel world, BlockPos pos) {
        LOGGER.info("Accumulating at {}", pos);
        world.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        EvaData.get(world).changeHumidity(pos, -1);
    }

    /**
     * Evaporate water at the given position (and track it in world data).
     *
     * This method handles evaporating partial water blocks, if using a water physics mod that supports it.
     */
    private static void evaporateAtPosition(ServerLevel world, BlockPos pos) {
        BlockPos evapPos = null;

        if (world.getFluidState(pos).getType() == Fluids.WATER) {
            evapPos = pos;
        }

        if (world.getFluidState(pos).getType() == Fluids.FLOWING_WATER) {
            evapPos = findNearestSource(world, pos, 20);
        }

        if (evapPos != null) {
            LOGGER.debug("Evaporating at {}" + (evapPos.equals(pos) ? "" : " (searched from {})"), evapPos, pos);
            world.setBlockAndUpdate(evapPos, Blocks.AIR.defaultBlockState());
            EvaData.get(world).changeHumidity(evapPos, 1);
        }
    }

    /**
     * Given a {@link BlockPos} containing flowing fluid, searches for the location of the nearest source of that fluid.
     * This can return `null` if the starting position isn't a fluid, or we can't find a source within the step limit.
     */
    @Nullable
    private static BlockPos findNearestSource(ServerLevel world, BlockPos pos, int maxSteps) {
        int step = 0;
        BlockPos curPos = pos;

        while (step < maxSteps && !world.getFluidState(curPos).isSource()) {
            Vec3 flow = world.getFluidState(curPos).getFlow(world, curPos);

            if (Vec3.ZERO.equals(flow)) {
                // Kind of a hail mary.
                /* TODO this is good enough for now, but won't work in cases where the flow of two nearby source blocks
                    has created a deadzone. If we want to handle that case, we'll instead want to also check if the
                     adjacent block is flowing water, and if not, look around us until we find one. */
                curPos = curPos.above();
            } else {
                curPos = new BlockPos(
                    curPos.getX() + (flow.x == 0 ? 0 : (flow.x < 0 ? 1 : -1)),
                    curPos.getY() + (flow.y == 0 ? 0 : (flow.y < 0 ? 1 : -1)),
                    curPos.getZ() + (flow.z == 0 ? 0 : (flow.z < 0 ? 1 : -1))
                );
            }

            step++;
        }

        return world.getFluidState(curPos).isSource() ? curPos : null;
    }

    /**
     * Gets the average amount of evaporated water stored in the given chunk and chunks surrounding it.
     * This is a weighted average based on distance from the given chunk.
     *
     * @param range how many adjacent chunks to search.
     *              e.g. if this value is 1, we average the given chunk with the 8 chunks surrounding it.
     * @return Amount in milli-buckets.
     */
    private static int getAverageHumidity(ServerLevel world, ChunkPos pos, int range) {
        // Yes, we're throwing away some precision by doing integer division,
        // but we're also dealing with milli-buckets, so whatever.
        return (int)ChunkUtil.getSurroundingChunkPos(pos, range)
            .stream()
            .mapToInt(chunkPos -> {
                Integer humidity = EvaData.get(world).getHumidity(chunkPos);
                // Make less of a chunk's humidity available the further away it is.
                int distance = Math.max(Math.abs(pos.x - chunkPos.x), Math.abs((pos.z - chunkPos.z)));
                return (humidity / (distance + 1)) + ((humidity * distance) / (int)Math.pow(distance, 2));
            })
            .average()
            .orElse(0);
    }

    /**
     * Returns the percent chance rain should accumulate in the chunk this block is located in.
     * This value is based on the chunk's humidity (as well as surrounding chunks).
     */
    private static double getAccumulationChance(ServerLevel world, BlockPos pos) {
        // TODO this is a placeholder calculation. Eventually this should be based on other things.
        // TODO If evaporation is disabled, this needs to be a whole different thing.
        //return getAverageHumidity(world, new ChunkPos(pos), 1);
        return world.random.nextDouble(100);
    }

    /**
     * Returns the percent chance water should evaporate in the chunk this block is located in.
     * This value is based on the chunk's humidity (as well as surrounding chunks).
     */
    public static double getEvaporationChance(ServerLevel world, BlockPos pos) {
        // TODO this is a placeholder calculation. Eventually this should be based on other things.
        // TODO if accumulation is disabled, this should be based on something else, like biome humidity.
        return world.random.nextDouble(100);
    }
}
