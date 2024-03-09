package mimickal.minecraft.waterworks.eva;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.Chance;
import mimickal.minecraft.util.ChunkUtil;
import mimickal.minecraft.util.TickGuard;
import mimickal.minecraft.waterworks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

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

        StreamSupport.stream(ChunkUtil.getLoadedChunks(world).spliterator(), false)
            .filter(chunkHolder -> distanceManager.inBlockTickingRange(chunkHolder.getPos().toLong()))
            .filter(chunkHolder -> Chance.percent(Config.accumulationIntensity.get()))
            .map(chunkHolder -> ChunkUtil.getRandomBlockInChunk(world, chunkHolder))
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
}
