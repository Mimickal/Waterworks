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
}
