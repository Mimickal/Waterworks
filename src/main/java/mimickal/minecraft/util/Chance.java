package mimickal.minecraft.util;

import java.util.Random;

public class Chance {
    private static final Random random = new Random();

    /** Returns `true` `chance` percent of the time. */
    public static boolean percent(double chance) {
        return random.nextDouble(100) < chance;
    }
}
