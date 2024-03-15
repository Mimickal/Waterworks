package mimickal.minecraft.waterworks.eva.events;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.Chance;
import mimickal.minecraft.util.ChunkUtil;
import mimickal.minecraft.util.TickGuard;
import mimickal.minecraft.waterworks.Config;
import mimickal.minecraft.waterworks.eva.EvaData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

public class Accumulation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, TickGuard.Config> TICK_GUARDS = new HashMap<>();

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

        TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard.Config(Config.accumulationSmoothness));
        if (!TICK_GUARDS.get(event.world.dimension()).ready()) return;

        ServerLevel world = (ServerLevel) event.world;
        DistanceManager distanceManager = world.getChunkSource().chunkMap.getDistanceManager();

        StreamSupport.stream(ChunkUtil.getLoadedChunks(world).spliterator(), false)
            .filter(chunkHolder -> distanceManager.inBlockTickingRange(chunkHolder.getPos().toLong()))
            .filter(chunkHolder -> Chance.percent(Chance.scaleWithSmoothness(
                Config.accumulationIntensity.get(), Config.accumulationSmoothness.get()
            )))
            .map(chunkHolder -> ChunkUtil.getRandomBlockInChunk(world, chunkHolder))
            .filter(chunkBlockPos -> world.getBiome(chunkBlockPos).value().getPrecipitation() == Biome.Precipitation.RAIN)
            .filter(chunkBlockPos -> Chance.percent(getAccumulationChance(world, chunkBlockPos)))
            .map(chunkBlockPos -> world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chunkBlockPos))
            .forEach(waterPos -> accumulateAtPosition(world, waterPos));
    }

    /**
     * Accumulate water at the given position (and track it in world data).
     *
     * This method handles placing partial water blocks, if using a water physics mod that supports it.
     */
    private static void accumulateAtPosition(ServerLevel world, BlockPos pos) {
        LOGGER.debug("Accumulating at {}", pos);
        world.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        EvaData.get(world).changeHumidity(pos, -1);
    }

    /**
     * Returns the percent chance rain should accumulate in the chunk this block is located in.
     * This is determined by the "downfall" value of the biome the block resides in.
     */
    private static double getAccumulationChance(ServerLevel world, BlockPos pos) {
        return world.getBiome(pos).value().getDownfall();
    }
}
