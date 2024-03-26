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

import java.util.function.Function;
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
     * Gets a horizontal slice of blocks in a chunk.
     * <p>
     * This is essentially a cartesian product of the chunk's x and z block ranges.<br/>
     * {@code range(minX, maxX) x range(minZ, maxZ)}
     */
    public static Stream<BlockPos> blocksInChunkArea(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        return IntStream.rangeClosed(pos.getMinBlockX(), pos.getMaxBlockX()).mapToObj(x -> (
            IntStream.rangeClosed(pos.getMinBlockZ(), pos.getMaxBlockZ()).mapToObj(z -> (
                new BlockPos(x, 0, z)
            ))
        )).flatMap(Function.identity());
    }
}
