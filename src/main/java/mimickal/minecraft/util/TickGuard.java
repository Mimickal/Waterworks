package mimickal.minecraft.util;

import net.minecraftforge.common.ForgeConfigSpec;

/** An easy wrapper for only running an event every X ticks. Contains several variants. */
public abstract class TickGuard {
    protected int counter;
    protected int delay;

    protected TickGuard() {
        this.counter = 0;
    }

    public abstract void updateDelay();

    public final boolean ready() {
        if (this.counter < this.delay) {
            this.counter++;
            return false;
        } else {
            // Optimization: only recalculate tick delay when we hit delay.
            // This lets us stay reasonably reactive to config changes without crunching numbers every tick.
            updateDelay();
            counter = 0;
            return true;
        }
    }

    /** A {@link TickGuard} based on a configuration smoothness percentage. */
    public static class Config extends TickGuard {
        private static final int MAX_TICK_DELAY = 5 * 20; // Roughly 5 seconds

        private final ForgeConfigSpec.DoubleValue smoothness;

        public Config(ForgeConfigSpec.DoubleValue smoothness) {
            super();
            this.smoothness = smoothness;
            updateDelay();
        }

        @Override
        public void updateDelay() {
            this.delay = (int)((100 - this.smoothness.get()) / 100 * MAX_TICK_DELAY);
        }
    }

    /** A {@link TickGuard} that picks a random delay within a range. */
    public static class Random extends TickGuard {
        private static final java.util.Random RANDOM = new java.util.Random();

        private final int lower;
        private final int upper;

        public Random(int lower, int upper) {
            super();
            this.lower = lower;
            this.upper = upper;
            updateDelay();
        }

        @Override
        public void updateDelay() {
            this.delay = RANDOM.nextInt(this.lower, this.upper + 1);
        }
    }
}
