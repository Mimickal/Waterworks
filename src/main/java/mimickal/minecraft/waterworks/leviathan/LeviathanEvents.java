package mimickal.minecraft.waterworks.leviathan;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class LeviathanEvents {
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
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("ServerChunkCache.getChunks() isn't a method, apparently.", e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke ServerChunkCache.getChunks()", e);
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Decides when to do water accumulation and evaporation events. */
    @SubscribeEvent
    public static void onWorldTickEvent(TickEvent.WorldTickEvent event) {
        // For performance reasons, this function bails as soon as possible
        // (hence all the early-returns and continues).

        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;

        // TODO this helps, but we need to skip a lot of the chunks every time this runs too.
        if (event.world.random.nextInt(20) != 0) return;

        ServerLevel world = (ServerLevel) event.world;
        DistanceManager distance = world.getChunkSource().chunkMap.getDistanceManager();

        for (ChunkHolder chunkHolder : getLoadedChunks(world)) {
            ChunkPos chunkPos = chunkHolder.getPos();

            if (!distance.inBlockTickingRange(chunkPos.toLong())) continue;

            // A chunk can span multiple biomes if it's right on the edge.
            // Picking a random spot every time means that the rate of biome-specific weather events
            // will be roughly equal to the percentage of this chunk contained within that biome.
            // e.g. if 40% of the biome is Jungle, ~40% of the time we'll process a Jungle weather event.
            BlockPos chunkBlockPos = world.getBlockRandomPos(
                chunkPos.getMinBlockX(), 0 /* Y */, chunkPos.getMinBlockZ(), 15 /* Chunk width */
            );

            if (world.isRaining()) {
                Biome chunkBiome = world.getBiome(chunkBlockPos).value();
                if (chunkBiome.getPrecipitation() == Biome.Precipitation.SNOW) continue;

                BlockPos waterPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, chunkBlockPos);
                world.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
            } else {

            }
        }

        // We only want to have rain accumulate when:
        // - it's raining.
        // - the block is in a biome where it rains (e.g. not a desert).
        // - the block is visible to the sky.
        // - Rain can accumulate in the biome
        // - the selected block is not on the accumulation blacklist.
        // We only want to evaporate when:
        // - it's not raining.
        // - the block is water.
        // - the block is visible to the sky.
        // Also for performance, we probably don't want to check this every tick.
    }
}
