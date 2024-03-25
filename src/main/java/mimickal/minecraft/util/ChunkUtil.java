/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ChunkUtil {
    /** For some silly reason {@link ChunkMap#getChunks} is protected, so this is the workaround. */
    public static Iterable<ChunkHolder> getLoadedChunks(ServerLevel world) {
        // This method is made public at run-time via an Access Transformer.
        // See src/resources/META-INF/accesstransformer.cfg
        //
        // We could call this function directly everywhere we need it, but wrapping it in a function means
        // we only need to deal with this incorrect "this method is protected" error in one place.
        return world.getChunkSource().chunkMap.getChunks();
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
        return getRandomBlockInChunk(world, chunkPos);
    }
    
    public static BlockPos getRandomBlockInChunk(ServerLevel world, ChunkPos chunkPos) {
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
