package mimickal.minecraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ChunkUtil {
    /** For some silly reason {@link ChunkMap#getChunks} is protected, so this is the workaround. */
    public static Iterable<ChunkHolder> getLoadedChunks(ServerLevel world) {
        // Ironically, Reflection is probably the most portable way to do this.
        ChunkMap chunkMap = world.getChunkSource().chunkMap;
        try {
            Method getChunks = ChunkMap.class.getDeclaredMethod("getChunks");
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
     * <p>
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

    /**
     * Returns a list of chunks in range of the given chunk.
     * For example, with a range of 1, this function will return the 3x3 grid of {@link ChunkPos}
     * surrounding the given chunk (total of 9).
     * With a range of 2, this returns 5x5 {@link ChunkPos} (total of 25).
     */
    public static List<ChunkPos> getSurroundingChunkPos(ChunkPos pos, int range) {
        int size = (int)Math.pow(range + 1, 2);
        List <ChunkPos> surrounding = new ArrayList<>(size);

        for (int x : IntStream.rangeClosed(pos.x - range, pos.x + range).toArray()) {
            for (int z : IntStream.rangeClosed(pos.z - range, pos.z + range).toArray()) {
                surrounding.add(new ChunkPos(x, z));
            }
        }

        return surrounding;
    }
}
