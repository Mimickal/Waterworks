package mimickal.minecraft.waterworks.eva;

import com.mojang.logging.LogUtils;
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
    private static final String TAG_NAME = "humidity";

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
        // This calls two different versions of the constructor.
        return storage.computeIfAbsent(EvaData::new, EvaData::new, SAVE_NAME);
    }

    // TODO This could get prohibitively large if a world gets big enough.
    /** A measure of water currently "evaporated" per-chunk. */
    private final Map<ChunkPos, Integer> humidity;

    /** This constructor is called when loading the first time (i.e. no data on disk). */
    private EvaData() {
        humidity = new HashMap<>();
    }

    /**
     * This constructor is called when deserializing from disk.
     * @param topLevelTag the incoming serialized data from disk.
     */
    private EvaData(CompoundTag topLevelTag) {
        this.humidity = topLevelTag.getList(TAG_NAME, Tag.TAG_COMPOUND)
            .stream()
            .map(tag -> (CompoundTag) tag)
            .collect(HashMap::new, HumidityTag::toMap, HashMap::putAll);
        LOGGER.debug("Loaded {}", this.humidity);
    }

    /**
     * Serializes the data out to disk.
     * @param topLevelTag A defined but empty tag to write the data into.
     * @return The populated tag.
     */
    @NotNull
    @Override
    public CompoundTag save(CompoundTag topLevelTag) {
        ListTag humidityList = this.humidity.entrySet()
            .stream()
            .map(HumidityTag::new)
            .collect(ListTag::new, ListTag::add, ListTag::addAll);

        topLevelTag.put(TAG_NAME, humidityList);
        LOGGER.debug("Serializing {}", humidityList);
        return topLevelTag;
    }

    /**
     * Gets the amount of evaporated water stored for the given chunk.
     * @return Amount in milli-buckets.
     */
    public Integer getHumidity(ChunkPos pos) {
        return this.humidity.getOrDefault(pos, 0);
    }

    /**
     * Gets the amount of evaporated water stored for the chunk the given block pos resides in.
     * @return Amount in milli-buckets.
     */
    public Integer getHumidity(BlockPos pos) {
        return getHumidity(new ChunkPos(pos));
    }

    /**
     * Changes the amount of evaporated water stored for the given chunk.
     * @param amountChanged amount in milli-buckets.
     */
    public void changeHumidity(ChunkPos pos, Integer amountChanged) {
        LOGGER.debug("Humidity change {} at chunk {}", amountChanged, pos);
        this.humidity.putIfAbsent(pos, 0);
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

    /** Helper for serializing and deserializing humidity data. */
    private static class HumidityTag extends CompoundTag {
        private static final String X = "x";
        private static final String Z = "z";
        private static final String AMOUNT = "amt";

        /** Serializes a single entry from {@link #humidity} map to a {@link CompoundTag}. */
        private HumidityTag(Map.Entry<ChunkPos, Integer> entry) {
            this.putInt(X, entry.getKey().x);
            this.putInt(Z, entry.getKey().z);
            this.putInt(AMOUNT, entry.getValue());
        }

        /** Deserializes a {@link CompoundTag} into the given Map, which eventually becomes {@link #humidity}. */
        private static void toMap(Map<ChunkPos, Integer> map, CompoundTag tag) {
            map.put(new ChunkPos(
                tag.getInt(X),
                tag.getInt(Z)
            ),  tag.getInt(AMOUNT));
        }
    }
}
