/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.waterworks;

import mimickal.minecraft.waterworks.decoration.StatueBlock;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> REGISTRY =
        DeferredRegister.create(ForgeRegistries.BLOCKS, Waterworks.MOD_NAME);

    /** Register a block. This will also register an item. See {@link ModItems#registerBlockItem}. */
    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> registeredBlock = REGISTRY.register(name, block);
        ModItems.registerBlockItem(name, registeredBlock, new Item.Properties().tab(CreativeModeTab.TAB_MISC));
        return registeredBlock;
    }

    public static final RegistryObject<Block> STATUE = register("statue",
        () -> new StatueBlock(BlockBehaviour.Properties
            .of(Material.METAL)
            .strength(0.2F)
            .destroyTime(0.5F)
            .sound(SoundType.METAL)
        )
    );
}
