/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.waterworks.eva;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.ChunkUtil;
import mimickal.minecraft.waterworks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/** Controls modification, serialization, and deserialization of data for the mod. */
public class EvaData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SAVE_NAME = "eva";
    private static final String HUMIDITY_TAG_NAME = "humidity";
    private static final String STATUE_TAG_NAME = "statue";

    /**
     * Gets the data manager for the given level, creating it if it doesn't exist yet.
     * <p>
     * This method adds a data manager to the level if it doesn't have one yet.
     * The created instance is registered with the level's storage. When the game is saved,
     * the level calls {@link EvaData#save(CompoundTag)}.
     * @param level The level whose data store to load.
     * @throws RuntimeException if accessed client-side.
     */
    @NotNull
    public static EvaData get(ServerLevel level) {
        if (level.isClientSide) {
            throw new RuntimeException(EvaData.class + " can only be accessed server-side");
        }

        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
            (loadedDataTag) -> new EvaData(level, loadedDataTag),
            () -> new EvaData(level),
            SAVE_NAME
        );
    }

    // TODO This could get prohibitively large if a world gets big enough.
    /** A measure of water currently "evaporated" per-chunk. */
    private final ChunkValueMap humidity;
    /** A count of leviathan statues places per-chunk. This cache avoids needing to search the whole chunk. */
    private final ChunkValueMap statues;
    private final ServerLevel world;

    /** This constructor is called when loading the first time (i.e. no data on disk). */
    private EvaData(ServerLevel world) {
        this.world = world;
        humidity = new ChunkValueMap();
        statues = new ChunkValueMap();
    }

    /**
     * This constructor is called when deserializing from disk.
     * @param topLevelTag the incoming serialized data from disk.
     */
    private EvaData(ServerLevel world, CompoundTag topLevelTag) {
        this.world = world;
        this.humidity = deserializeToMap(topLevelTag.getList(HUMIDITY_TAG_NAME, Tag.TAG_COMPOUND));
        this.statues = deserializeToMap(topLevelTag.getList(STATUE_TAG_NAME, Tag.TAG_COMPOUND));
        LOGGER.debug("Loaded humidity data ({} chunks)", this.humidity.size());
        LOGGER.debug("Loaded statue data ({} chunks)", this.statues.size());
    }

    /**
     * Serializes the data out to disk.
     * @param topLevelTag A defined but empty tag to write the data into.
     * @return The populated tag.
     */
    @NotNull
    @Override
    public CompoundTag save(CompoundTag topLevelTag) {
        ListTag humidityList = serializeToTag(this.humidity);
        ListTag statueList = serializeToTag(this.statues);
        topLevelTag.put(HUMIDITY_TAG_NAME, humidityList);
        topLevelTag.put(STATUE_TAG_NAME, statueList);
        LOGGER.debug("Saving humidity data ({} chunks)", humidityList.size());
        LOGGER.debug("Saving statue data ({} chunks)", statueList.size());
        return topLevelTag;
    }

    /* Humidity functions */

    /**
     * Gets the amount of evaporated water stored for the given chunk.
     * @return Amount in milli-buckets.
     */
    @NotNull
    public Integer getHumidity(ChunkPos pos) {
        this.humidity.computeIfAbsent(pos, this::calcInitialHumidity);
        return this.humidity.get(pos);
    }

    /**
     * Gets the amount of evaporated water stored for the chunk the given block pos resides in.
     * @return Amount in milli-buckets.
     */
    @NotNull
    public Integer getHumidity(BlockPos pos) {
        return getHumidity(new ChunkPos(pos));
    }

    /**
     * Changes the amount of evaporated water stored for the given chunk.
     * @param amountChanged amount in milli-buckets.
     */
    public void changeHumidity(ChunkPos pos, Integer amountChanged) {
        LOGGER.debug("Humidity change {} at chunk {}", amountChanged, pos);
        this.humidity.computeIfAbsent(pos, this::calcInitialHumidity);
        this.humidity.put(pos, this.humidity.get(pos) + amountChanged);
        this.setDirty();
    }

    /**
     * Changes the amount of evaporated water stored for the chunk the given block pos resides in.
     * @param amountChanged amount in milli-buckets.
     */
    public void changeHumidity(BlockPos pos, Integer amountChanged) {
        changeHumidity(new ChunkPos(pos), amountChanged);
    }

    /**
     * <b>Sets</b> the amount of evaporated water stored for the given chunk.
     * @param amount amount in milli-buckets.
     */
    public void setHumidity(ChunkPos pos, Integer amount) {
        LOGGER.debug("Humidity set {} at chunk {}", amount, pos);
        this.humidity.put(pos, amount);
        this.setDirty();
    }

    /**
     * <b>Sets</b> the amount of evaporated water stored for the chunk the given block pos resides in.
     * @param amount amount in milli-buckets.
     */
    public void setHumidity(BlockPos pos, Integer amount) {
        setHumidity(new ChunkPos(pos), amount);
    }

    /** <b>Resets</b> the amount of evaporated water stored for the given chunk. */
    public void resetHumidity(ChunkPos pos) {
        // This will regenerate using the default value next time we try to do something with this chunk.
        LOGGER.debug("Humidity unset at chunk {}", pos);
        this.humidity.remove(pos);
        this.setDirty();
    }

    /** <b>Resets</b> the amount of evaporated water stored for the chunk the given block pos resides in. */
    public void resetHumidity(BlockPos pos) {
        resetHumidity(new ChunkPos(pos));
    }

    /** <b>COMPLETELY DELETES</b> the map of evaporated water stored for this level (NOTE: <i>level</i>, not world). */
    public void resetAllHumidity(boolean seriously) {
        // Like with reset, every chunk's default value will be regenerated next time they're accessed.
        if (!seriously) return;
        LOGGER.debug("CLEARING humidity map for {}", this.world.dimension().location());
        this.humidity.clear();
        this.setDirty();
    }

    /**
     * Calculates the initial humidity for the given chunk.
     * <p>
     * A chunk can span more than one biome. When {@link Config#chunkVanillaHumidity} is enabled, we just pick
     * a random block in the chunk and use that block's biome's downfall value for the calculation.
     */
    private Integer calcInitialHumidity(ChunkPos pos) {
        BlockPos blockPos = ChunkUtil.getRandomPosInChunk(this.world, pos);
        return calcInitialHumidity(blockPos);
    }

    /**
     * Calculates the initial humidity for the chunk the given block pos resides in.
     * <p>
     * Initial humidity is a percentage of the configured "100% will rain" threshold,
     * optionally scaled by the {@link net.minecraft.world.level.biome.Biome#getDownfall()}
     * value of the biome this block lives in.
     * <p>
     * This allows biomes that should intuitively be humid to generate humid (e.g. the ocean).
     * This avoids a large delay before the first rainstorm in a new world, whereas starting at 0 humidity would
     * require a lot of evaporation to happen before rain would happen.
     */
    private Integer calcInitialHumidity(BlockPos pos) {
        int humidity = (int)(
            Config.chunkDefaultHumidityPercent.get() / 100 *
            Config.rainChunkHumidityThreshold.get() *
            (Config.chunkVanillaHumidity.get()
                ? this.world.getBiome(pos).value().getDownfall()
                : 1
            )
        );

        LOGGER.debug("Initializing {} with {}", new ChunkPos(pos), humidity);
        return humidity;
    }

    /* Statue functions */

    /** Gets the number of statues in the given chunk. */
    @NotNull
    public Integer getStatueCount(ChunkPos pos) {
        return this.statues.getOrDefault(pos, 0);
    }

    /** Gets the number of statues in the chunk the given block pos resides in. */
    @NotNull
    public Integer getStatueCount(BlockPos pos) {
        return getStatueCount(new ChunkPos(pos));
    }

    /** Changes the number of statues stored for the given chunk. Deletes the entry if resulting value is 0. */
    public void changeStatueCount(ChunkPos pos, Integer amountChanged) {
        LOGGER.debug("Statue count change {} at chunk {}", amountChanged, pos);
        this.statues.put(pos, this.statues.getOrDefault(pos, 0) + amountChanged);

        // To save space, delete entries for chunks with no statues (i.e. most of them).
        if (this.statues.get(pos) == 0) {
            this.statues.remove(pos);
        }

        this.setDirty();
    }

    /** Changes the number of statues stored for the chunk the given block pos resides in. */
    public void changeStatueCount(BlockPos pos, Integer amountChanged) {
        changeStatueCount(new ChunkPos(pos), amountChanged);
    }

    /* Helpers */

    /** Specialization of a {@link HashMap} that maps numerical values by chunk. */
    private static class ChunkValueMap extends HashMap<ChunkPos, Integer> { }

    private static ChunkValueMap deserializeToMap(ListTag listTag) {
        return listTag
            .stream()
            .map(tag -> (CompoundTag) tag)
            .collect(ChunkValueMap::new, ChunkAmountTag::toMap, ChunkValueMap::putAll);
    }

    private static ListTag serializeToTag(ChunkValueMap map) {
        return map.entrySet()
            .stream()
            .map(ChunkAmountTag::new)
            .collect(ListTag::new, ListTag::add, ListTag::addAll);
    }

    /** Helper for serializing and deserializing integer data for a chunk. */
    private static class ChunkAmountTag extends CompoundTag {
        private static final String X = "x";
        private static final String Z = "z";
        private static final String AMOUNT = "amt";

        /** Serializes a single entry from a chunk map to a {@link CompoundTag}. */
        private ChunkAmountTag(Map.Entry<ChunkPos, Integer> entry) {
            this.putInt(X, entry.getKey().x);
            this.putInt(Z, entry.getKey().z);
            this.putInt(AMOUNT, entry.getValue());
        }

        /** Deserializes a {@link CompoundTag} into the given Map, which eventually becomes a chunk map. */
        private static void toMap(Map<ChunkPos, Integer> map, CompoundTag tag) {
            map.put(new ChunkPos(
                tag.getInt(X),
                tag.getInt(Z)
            ),  tag.getInt(AMOUNT));
        }
    }
}
