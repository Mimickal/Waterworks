package mimickal.minecraft.waterworks.tool;

import mimickal.minecraft.waterworks.Waterworks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Items {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, Waterworks.MOD_NAME);

    public static final RegistryObject<Item> AQUAMARINE = ITEMS.register("aquamarine",
        () -> new Item(new Item.Properties().tab(CreativeModeTab.TAB_MISC)));

    public static final TagKey<Item> AQUAMARINE_GEM =
        ItemTags.create(new ResourceLocation(Waterworks.MOD_NAME, "aquamarine"));
}
