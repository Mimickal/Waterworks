package mimickal.minecraft.util;

import java.util.Random;

public class Chance {
    private static final Random random = new Random();

    /** Returns `true` `chance` percent of the time. */
    public static boolean percent(double chance) {
        return random.nextDouble(100) < chance;
    }

    /** Treats a decimal between 0 and 1 as a percent, and returns `true` that percent of the time. */
    public static boolean decimal(double dec) {
        return random.nextDouble() < dec;
    }

    /**
     * Scales the chance of a single event to be inversely proportional with its smoothness.
     * <p>
     * Intensity is used to determine the chance that a single event happens.
     * Smoothness is used to determine how often we "roll" on that chance.
     * Scaling chance against smoothness means the same number of events should occur over time regardless of smoothness
     * (i.e. number of events over time is determines solely by intensity).
     */
    public static double scaleWithSmoothness(double intensity, double smoothness) {
        if (smoothness < 1) {
            return intensity;
        }

        return intensity / smoothness;
    }
}
