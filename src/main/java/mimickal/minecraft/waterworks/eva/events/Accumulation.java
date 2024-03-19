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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Accumulation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, TickGuard.Config> TICK_GUARDS = new HashMap<>();

    /**
     * {@link Config#accumulationBlacklist} as a {@link Set}, to speed up filter operations.
     * This is recalculated by {@link #onBlacklist} when the underlying config changes.
     */
    private static Set<Block> BLACKLIST_SET = new HashSet<>();
    private static int BLACKLIST_HASH = 0;

    /**
     * {@link TickEvent.WorldTickEvent} handler that accumulates water when it's raining.
     * <p>
     * Rain only accumulates when:
     * <li>It's raining.</li>
     * <li>The block is in a biome where it rains (e.g. not a desert).</li>
     * <li>The block is visible to the sky.</li>
     * <li>Rain can accumulate in the biome.</li>
     * <li>the selected block is not on the accumulation blacklist.</li>
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
            .filter(waterPos -> !onBlacklist(world, waterPos.below()))
            .forEach(waterPos -> accumulateAtPosition(world, waterPos));
    }

    /**
     * Returns whether the block at the given position is on the configured blacklist.
     * <p>
     * Also, recalculates {@link #BLACKLIST_SET} if the underlying config has been modified.
     */
    private static boolean onBlacklist(ServerLevel world, BlockPos pos) {
        // Recalculate accumulation blacklist cache when the underlying list changes.
        // This may be some React brain rot setting in...
        if (Config.accumulationEnabled.get().hashCode() != BLACKLIST_HASH) {
            BLACKLIST_HASH = Config.accumulationBlacklist.get().hashCode();
            BLACKLIST_SET = Config.accumulationBlacklist.get()
                .stream()
                .map(resourceName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(resourceName)))
                .collect(Collectors.toSet());
        }

        Block block = world.getBlockState(pos).getBlock();
        boolean isOnList = BLACKLIST_SET.contains(block);

        if (isOnList) {
            LOGGER.debug("Block {} on blacklist ({})", pos, block);
        }

        return isOnList;
    }

    /**
     * Accumulate water at the given position (and track it in world data).
     * <p>
     * This method handles placing partial water blocks, if using a water physics mod that supports it.
     */
    private static void accumulateAtPosition(ServerLevel world, BlockPos pos) {
        LOGGER.debug("Accumulating at {}", pos);
        world.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        EvaData.get(world).changeHumidity(pos, -1000);
    }

    /**
     * Returns the percent chance rain should accumulate in the chunk this block is located in.
     * This is determined by the "downfall" value of the biome the block resides in.
     */
    private static double getAccumulationChance(ServerLevel world, BlockPos pos) {
        return world.getBiome(pos).value().getDownfall();
    }
}
