package mimickal.minecraft.waterworks.eva;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.waterworks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.StreamSupport;


public class EvaEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TickGuard ACCUM_TICK_GUARD = new TickGuard(Config.accumulationSmoothness);

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
        // NOTE: this check returns false when first starting a world that is already raining.
        if (!event.world.isRaining()) return;
        if (!ACCUM_TICK_GUARD.ready()) return;

        ServerLevel world = (ServerLevel) event.world;
        DistanceManager distanceManager = world.getChunkSource().chunkMap.getDistanceManager();

        StreamSupport.stream(getLoadedChunks(world).spliterator(), false)
            .filter(chunkHolder -> distanceManager.inBlockTickingRange(chunkHolder.getPos().toLong()))
            .filter(chunkHolder -> world.random.nextDouble(100) < Config.accumulationIntensity.get())
            .map(chunkHolder -> getRandomBlockInChunk(world, chunkHolder))
            .filter(chunkBlockPos -> world.getBiome(chunkBlockPos).value().getPrecipitation() == Biome.Precipitation.RAIN)
            .forEach(chunkBlockPos -> {
                BlockPos waterPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, chunkBlockPos);
                LOGGER.info("Raining at " + waterPos);
                world.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
            });
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

    }

    /** For some silly reason {@link ChunkMap#getChunks} is protected, so this is the workaround. */
    private static Iterable<ChunkHolder> getLoadedChunks(ServerLevel world) {
        // Ironically, Reflection is probably the most portable way to do this.
        try {
            ChunkMap chunkMap = world.getChunkSource().chunkMap;
            Method getChunks = chunkMap.getClass().getDeclaredMethod("getChunks");
            getChunks.setAccessible(true);

            // AFAIK there's no way to do this cast that Java thinks is safe.
            // ChunkMap.getChunks() only ever returns this type, so it's safe enough.
            @SuppressWarnings("unchecked")
            Iterable<ChunkHolder> chunkIterator = (Iterable<ChunkHolder>) getChunks.invoke(chunkMap);

            return chunkIterator;

            // Any of these exceptions being thrown means we messed something up above, so just explode.
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("ServerChunkCache.getChunks() isn't a method, apparently.", e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke ServerChunkCache.getChunks()", e);
        }
    }

    /**
     * Returns a random block in the given chunk.
     *
     * A chunk can span multiple biomes if it's right on the edge.
     * Picking a random spot every time means that the rate of biome-specific weather events
     * will be roughly equal to the percentage of this chunk contained within that biome.
     * e.g. if 40% of the biome is Jungle, ~40% of the time we'll process a Jungle weather event.
     */
    private static BlockPos getRandomBlockInChunk(ServerLevel world, ChunkHolder chunkHolder) {
        ChunkPos chunkPos = chunkHolder.getPos();
        return world.getBlockRandomPos(
            chunkPos.getMinBlockX(), 0 /* Y */, chunkPos.getMinBlockZ(), 15 /* Chunk width */
        );
    }

    /** An easy wrapper for only running an event every X ticks, based on config. */
    private static class TickGuard {
        private static final int MAX_TICK_DELAY = 5 * 20; // Roughly 5 seconds

        private int counter;
        private int delay;
        private final ForgeConfigSpec.DoubleValue smoothness;

        public TickGuard(ForgeConfigSpec.DoubleValue smoothness) {
            this.smoothness = smoothness;
            this.counter = 0;
            recalcRunAt();
        }

        private void recalcRunAt() {
            this.delay = (int)((100 - smoothness.get()) / 100 * MAX_TICK_DELAY);
        }

        public boolean ready() {
            if (counter < this.delay) {
                counter++;
                return false;
            } else {
                // Optimization: only recalculate tick delay when we hit delay.
                // This lets us stay reasonably reactive to config changes without crunching numbers every tick.
                recalcRunAt();
                counter = 0;
                return true;
            }
        }
    }
}
