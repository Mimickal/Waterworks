package mimickal.minecraft.util;

import net.minecraftforge.common.ForgeConfigSpec;

/** An easy wrapper for only running an event every X ticks, based on config. */
public class TickGuard {
    private static final int MAX_TICK_DELAY = 5 * 20; // Roughly 5 seconds

    private int counter;
    private int delay;
    private final ForgeConfigSpec.DoubleValue smoothness;

    public TickGuard(ForgeConfigSpec.DoubleValue smoothness) {
        this.smoothness = smoothness;
        this.counter = 0;
        recalcRunAt();
    }

    private void recalcRunAt() {
        this.delay = (int)((100 - smoothness.get()) / 100 * MAX_TICK_DELAY);
    }

    public boolean ready() {
        if (counter < this.delay) {
            counter++;
            return false;
        } else {
            // Optimization: only recalculate tick delay when we hit delay.
            // This lets us stay reasonably reactive to config changes without crunching numbers every tick.
            recalcRunAt();
            counter = 0;
            return true;
        }
    }
}
