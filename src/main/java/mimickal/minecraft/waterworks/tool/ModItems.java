/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.waterworks.tool;

import mimickal.minecraft.waterworks.Waterworks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, Waterworks.MOD_NAME);

    /** Register an item with properties. */
    public static RegistryObject<Item> register(String name, Item.Properties properties) {
        return ITEMS.register(name, () -> new Item(properties));
    }

    /** Register an item from a block. */
    public static RegistryObject<Item> registerBlockItem(
        String name,
        RegistryObject<Block> registeredBlock,
        Item.Properties properties
    ) {
        return ITEMS.register(name, () -> new BlockItem(registeredBlock.get(), properties));
    }

    public static final RegistryObject<Item> AQUAMARINE = register("aquamarine",
        new Item.Properties().tab(CreativeModeTab.TAB_MISC));

    public static final TagKey<Item> AQUAMARINE_GEM =
        ItemTags.create(new ResourceLocation(Waterworks.MOD_NAME, "aquamarine"));
}
