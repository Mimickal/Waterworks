package mimickal.minecraft.waterworks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.stream.Stream;

public class Config {
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static final String CONFIG_FILENAME = Waterworks.MOD_NAME + ".toml";
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        buildAccumulationConfig(builder);
        addEvaporationConfig(builder);
        CONFIG_SPEC = builder.build();
    }

    public static ForgeConfigSpec.BooleanValue accumulationEnabled;
    public static ForgeConfigSpec.DoubleValue accumulationIntensity;
    public static ForgeConfigSpec.DoubleValue accumulationSmoothness;
    public static ForgeConfigSpec.ConfigValue<Integer> accumulationMaxHeight;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> accumulationBlacklist;

    public static ForgeConfigSpec.BooleanValue evaporationEnabled;
    public static ForgeConfigSpec.DoubleValue evaporationIntensity;
    public static ForgeConfigSpec.DoubleValue evaporationSmoothness;
    public static ForgeConfigSpec.ConfigValue<Integer> evaporationMaxHeight;
    public static ForgeConfigSpec.DoubleValue evaporationSunCoefficient;

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
                "Never accumulate water on top of these blocks.",
                "Useful to avoid breaking surface lava and crops with accumulated water."
            )
            .defineList("blacklist",
                Stream.of(
                    Blocks.LAVA,

                    // Fences
                    // TODO this sucks. Can we define all fences in one go?
                    Blocks.ACACIA_FENCE,
                    Blocks.BIRCH_FENCE,
                    Blocks.CRIMSON_FENCE,
                    Blocks.JUNGLE_FENCE,

                    // Crops
                    // TODO this also sucks for the same reason
                    Blocks.BEETROOTS,
                    Blocks.MELON,
                    Blocks.MELON_STEM,
                    Blocks.PUMPKIN,
                    Blocks.PUMPKIN_STEM,
                    Blocks.WHEAT

                    // TODO tilled soil?
                ).map(Block::getDescriptionId).toList(),
                // TODO Actually validate these are real blocks
                block -> true
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

        builder.pop();
    }
}
