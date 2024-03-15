package mimickal.minecraft.waterworks.eva.events;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.TickGuard;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class Rain {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, TickGuard.Random> TICK_GUARDS = new HashMap<>();

    // TODO just copied from ServerLevel for now
    private static final int RAIN_DELAY_MIN = 12000;  // 10 minutes
    private static final int RAIN_DELAY_MAX = 180000; // 150 minutes

    @SubscribeEvent
    public static void accumulateWhenRaining(TickEvent.WorldTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        // TODO can we short-circuit if the world doesn't ever rain?
        if (!event.world.isRaining()) return;

        TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard.Random(RAIN_DELAY_MIN, RAIN_DELAY_MAX));
        if (!TICK_GUARDS.get(event.world.dimension()).ready()) return;

        LOGGER.debug("Testing rain chance");
    }
}
