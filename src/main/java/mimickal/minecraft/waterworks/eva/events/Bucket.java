/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.waterworks.eva.events;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.util.Chance;
import mimickal.minecraft.util.TickGuard;
import mimickal.minecraft.waterworks.Config;
import mimickal.minecraft.waterworks.eva.EvaData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class Bucket {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, TickGuard.Constant> TICK_GUARDS = new HashMap<>();
    private static final int CHECK_DELAY = 30 * 20; // 5 seconds
    private static final ItemStack SINGLE_BUCKET = new ItemStack(Items.BUCKET, 1);

    /**
     * {@link TickEvent.WorldTickEvent} handler that fills a player's bucket with water if they're holding it
     * while standing in the rain. This must be a single bucket, not a stack, since water buckets don't stack.
     */
    @SubscribeEvent
    public static void fillHeldBuckets(TickEvent.WorldTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (!Config.evaporationEnabled.get()) return;
        if (!event.world.isRaining()) return;

        TICK_GUARDS.putIfAbsent(event.world.dimension(), new TickGuard.Constant(CHECK_DELAY));
        if (!TICK_GUARDS.get(event.world.dimension()).ready()) return;

        ServerLevel world = (ServerLevel) event.world;

        world.players()
            .stream()
            .filter(Entity::isInRain) // Access Transformed to be public
            .filter(player -> player.isHolding(item -> ItemStack.matches(item, SINGLE_BUCKET)))
            .filter(player -> Chance.decimal(getBucketFillChance(world, player.getOnPos())))
            .forEach(player -> replacePlayerHeldBucketWithWaterBucket(world, player));
    }

    /**
     * Returns the chance (as a {@link Double} 0.0 - 1.0) rain should accumulate in a player's held bucket.
     * This is determined by the "downfall" value of the biome the player is standing in.
     * Having a {@link mimickal.minecraft.waterworks.ModBlocks#STATUE} in the chunk also slightly increases the chance.
     */
    private static double getBucketFillChance(ServerLevel world, BlockPos pos) {
        double chanceMod = EvaData.get(world).getStatueCount(pos) > 0 ? 0.1 : 0;
        return world.getBiome(pos).value().getDownfall() + chanceMod;
    }

    /**
     * If a player is holding a bucket, fill the bucket with water.<br>
     * Only does one hand at a time so players double-fisting buckets get a more natural feeling effect.
     */
    private static void replacePlayerHeldBucketWithWaterBucket(ServerLevel world, ServerPlayer player) {
        if (ItemStack.matches(player.getItemInHand(InteractionHand.MAIN_HAND), SINGLE_BUCKET)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        } else if (ItemStack.matches(player.getItemInHand(InteractionHand.OFF_HAND), SINGLE_BUCKET)) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
        }

        LOGGER.debug("Giving water bucket to {}", player);
        EvaData.get(world).changeHumidity(player.getOnPos(), -1000);
    }
}
