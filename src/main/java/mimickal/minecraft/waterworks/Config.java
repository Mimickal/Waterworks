package mimickal.minecraft.waterworks;

import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistryEntry;

import java.util.List;
import java.util.Objects;

public class Config {
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static final String CONFIG_FILENAME = Waterworks.MOD_NAME + ".toml";

    // Accumulation fields
    public static ForgeConfigSpec.BooleanValue accumulationEnabled;
    public static ForgeConfigSpec.DoubleValue accumulationIntensity;
    public static ForgeConfigSpec.DoubleValue accumulationSmoothness;
    public static ForgeConfigSpec.ConfigValue<Integer> accumulationMaxHeight;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> accumulationBlacklist;

    // Evaporation fields
    public static ForgeConfigSpec.BooleanValue evaporationEnabled;
    public static ForgeConfigSpec.DoubleValue evaporationIntensity;
    public static ForgeConfigSpec.DoubleValue evaporationSmoothness;
    public static ForgeConfigSpec.IntValue evaporationTenacity;
    public static ForgeConfigSpec.ConfigValue<Integer> evaporationMaxHeight;
    public static ForgeConfigSpec.DoubleValue evaporationSunCoefficient;
    public static ForgeConfigSpec.BooleanValue chunkVanillaHumidity;
    public static ForgeConfigSpec.ConfigValue<Double> chunkDefaultHumidityPercent;

    // Rain fields
    public static ForgeConfigSpec.BooleanValue rainModEnabled;
    public static ForgeConfigSpec.ConfigValue<Integer> rainChunkHumidityThreshold;

    // Constants
    // These need to be defined before the below static block
    private static final List<Class<? extends Block>> BLACKLIST_BLOCK_GROUPS = ImmutableList.of(
        CropBlock.class,
        FarmBlock.class,
        FenceBlock.class,
        StemBlock.class
    );

    private static final List<Block> BLACKLIST_BLOCKS = ImmutableList.of(
        Blocks.LAVA,
        Blocks.MELON,
        Blocks.PUMPKIN
    );

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        buildAccumulationConfig(builder);
        addEvaporationConfig(builder);
        addRainConfig(builder);
        CONFIG_SPEC = builder.build();
    }

    private static void buildAccumulationConfig(ForgeConfigSpec.Builder builder) {
        builder.comment(
            "WARNING - Higher intensity and smoothness values can be very hard on the CPU!",
            "They can also result in biblical floods! Use with care!",
            "",
            "Rain accumulation settings"
        );
        builder.push("accumulation");

        accumulationEnabled = builder
            .comment("Enable/disable the entire rain accumulation system.")
            .define("enabled", true);

        accumulationIntensity = builder
            .comment(
                "Intensity controls how much water accumulates at a time.",
                "Higher intensity means more loaded chunks are checked per-cycle."
            )
            .defineInRange("intensity_percent", 10d, 0d, 100d);

        accumulationSmoothness = builder
            .comment(
                "Smoothness controls how frequently water accumulates.",
                "Higher smoothness means less time between accumulation cycles."
            )
            .defineInRange("smoothness_percent", 20d, 0d, 100d);

        accumulationMaxHeight = builder
            .comment(
                "Controls how high water is allowed to accumulate above its starting height (in a biome).",
                "A negative value here means water will only accumulate to a height below its starting height."
            )
            .define("max_height", 5);

        accumulationBlacklist = builder
            .comment(
                "Never accumulate water on top of these blocks. These are the blocks' registry names.",
                "Useful to avoid breaking surface lava and crops with accumulated water."
            )
            .defineList("blacklist",
                ForgeRegistries.BLOCKS.getValues()
                    .stream()
                    .filter(block -> (
                        BLACKLIST_BLOCK_GROUPS.stream().anyMatch(klass -> klass.isInstance(block)) ||
                        BLACKLIST_BLOCKS.contains(block)
                    ))
                    .map(ForgeRegistryEntry::getRegistryName)
                    .filter(Objects::nonNull)
                    .map(ResourceLocation::toString)
                    .toList(),
                (blockName) -> (
                    blockName instanceof String &&
                    ForgeRegistries.BLOCKS.containsKey(new ResourceLocation((String) blockName))
                )
            );

        builder.pop();
    }

    private static void addEvaporationConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Water evaporation settings");
        builder.push("evaporation");

        evaporationEnabled = builder
            .comment("Enable/disable the entire water evaporation system.")
            .define("enabled", true);

        evaporationIntensity = builder
            .comment(
                "Intensity controls how much water evaporates at a time.",
                "Higher intensity means more loaded chunks are checked per-cycle."
            )
            .defineInRange("intensity_percent", 10d, 0d, 100d);

        evaporationSmoothness = builder
            .comment(
                "Smoothness controls how frequently water evaporates.",
                "Higher smoothness means less time between evaporation cycles."
            )
            .defineInRange("smoothness_percent", 20d, 0d, 100d);

        evaporationTenacity = builder
            .comment(
                "Tenacity controls how many flowing water blocks we'll search to find a source block.",
                "Higher values mean evaporation will succeed more often, but are harder on the CPU."
            )
            .defineInRange("tenacity", 30, 0, Integer.MAX_VALUE);

        evaporationMaxHeight = builder
            .comment(
                "Controls how low water is allowed to evaporate below its starting height (in a biome).",
                "A negative value here means water will only evaporate one it has reached a height above its starting height."
            )
            .define("max_height", 5);

        evaporationSunCoefficient = builder
            .comment(
                "Controls how much influence the sun has over evaporation.",
                "0 means the sun has no effect (i.e. evaporation is at its full strength at all hours of the day).",
                "1 means evaporation ONLY happens while the sun is out."
            )
            .defineInRange("sun_coefficient", 0.7d, 0d, 1d);

        chunkVanillaHumidity = builder
            .comment(
                "Determines a chunk's initial humidity using its biome's vanilla rain downfall value.",
                "For example, all chunks in a biome with a downfall value of 0.3 will start at a 30% chance of rain.",
                "This helps speed up the first rainstorm in new areas. If you're not sure, leave this enabled."
            )
            .define("initial_humidity_vanilla", true);

        chunkDefaultHumidityPercent = builder
            .comment(
                "Scale initial chunk humidity by this percentage.",
                "Values above 100 and negative values are acceptable, but will have adverse effects on the rain system."
            )
            .define("initial_humidity_percent", 50d);

        builder.pop();
    }

    private static void addRainConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Rain mechanics settings");
        builder.push("rain mechanics");

        rainModEnabled = builder
            .comment(
                "Override vanilla rain behavior.",
                "This makes the chance of rain be a function of \"humidity\", i.e. how much water has been evaporated.",
                "The higher the humidity, the more likely rain is. Rain will continue until humidity has come down.",
                "Accumulation and evaporation must also both be enabled for this setting to have an effect."
            )
            .define("enabled", true);

        rainChunkHumidityThreshold = builder
            .comment(
                "The amount of water evaporated in a chunk for it to be considered 100% humid, in milli-buckets.",
                "Lower values make rainstorms more frequent, but shorter."
            )
            .defineInRange("humidity_threshold", 5_000, 0, Integer.MAX_VALUE);

        builder.pop();
    }
}
