package mimickal.minecraft.waterworks.tool;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class Trades {

    private static final int LEVEL_NOVICE = 1;
    private static final int LEVEL_MASTER = 5;

    /**
     * Makes fishermen in villages occasionally have aquamarines in stock.
     */
    @SubscribeEvent
    public static void addVillagerTrades(VillagerTradesEvent event) {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        if (event.getType() == VillagerProfession.FISHERMAN) {
            // 2 Emerald -> 1 Aquamarine
            trades.get(LEVEL_NOVICE).add((trader, rand) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, 3),
                new ItemStack(ModItems.AQUAMARINE.get(), 1),
                2 /* Max trades */,
                8 /* XP */,
                0.02F /* Price multiplier */
            ));

            // 5 Emerald -> 3 Aquamarine
            trades.get(LEVEL_MASTER).add((trader, rand) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, 5),
                new ItemStack(ModItems.AQUAMARINE.get(), 3),
                8 /* Max trades */,
                8 /* XP */,
                0.02F /* Price multiplier */
            ));
        }
    }
}
