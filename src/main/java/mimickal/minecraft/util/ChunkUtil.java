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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ChunkUtil {
    /** Handles the weirdness needed to stream over all loaded chunks. */
    public static Stream<ChunkHolder> streamLoadedChunks(ServerLevel world) {
        // This method is made public via an Access Transformer.
        // See src/resources/META-INF/accesstransformer.cfg
        Iterable<ChunkHolder> chunks = world.getChunkSource().chunkMap.getChunks();
        return StreamSupport.stream(chunks.spliterator(), false);
    }

    /**
     * Returns a random block in the given chunk.
     * <p>
     * A chunk can span multiple biomes if it's right on the edge.
     * Picking a random spot every time means that the rate of biome-specific calculations
     * will be roughly equal to the percentage of this chunk contained within that biome.
     * e.g. if 40% of the biome is Jungle, ~40% of the time we'll get a Jungle block.
     */
    public static BlockPos getRandomPosInChunk(ServerLevel world, ChunkHolder chunkHolder) {
        return getRandomPosInChunk(world, chunkHolder.getPos());
    }

    public static BlockPos getRandomPosInChunk(ServerLevel world, LevelChunk chunk) {
        return getRandomPosInChunk(world, chunk.getPos());
    }
    
    public static BlockPos getRandomPosInChunk(ServerLevel world, ChunkPos chunkPos) {
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
