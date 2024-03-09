package mimickal.minecraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ChunkUtil {
    /** For some silly reason {@link ChunkMap#getChunks} is protected, so this is the workaround. */
    public static Iterable<ChunkHolder> getLoadedChunks(ServerLevel world) {
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
     * Picking a random spot every time means that the rate of biome-specific calculations
     * will be roughly equal to the percentage of this chunk contained within that biome.
     * e.g. if 40% of the biome is Jungle, ~40% of the time we'll get a Jungle block.
     */
    public static BlockPos getRandomBlockInChunk(ServerLevel world, ChunkHolder chunkHolder) {
        ChunkPos chunkPos = chunkHolder.getPos();
        return world.getBlockRandomPos(
            chunkPos.getMinBlockX(), 0 /* Y */, chunkPos.getMinBlockZ(), 15 /* Chunk width */
        );
    }
}
