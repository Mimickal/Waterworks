/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.waterworks;

import com.mojang.logging.LogUtils;
import mimickal.minecraft.waterworks.decoration.Painting;
import mimickal.minecraft.waterworks.eva.commands.HumidityCommand;
import mimickal.minecraft.waterworks.eva.events.*;
import mimickal.minecraft.waterworks.tool.Trades;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.stream.Stream;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Waterworks.MOD_NAME)
public class Waterworks
{
    public static final String MOD_NAME = "waterworks";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Waterworks()
    {
        // Register and load configuration
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.CONFIG_SPEC, Config.CONFIG_FILENAME);

        // Register events
        Stream.of(
            // Block placement
            Statue.class,

            // Tick
            Accumulation.class,
            Bucket.class,
            Evaporation.class,
            Rain.class,

            // Trades
            Trades.class,

            // Commands
            HumidityCommand.class
        ).forEach(MinecraftForge.EVENT_BUS::register);

        // Register registries
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();

        Stream.of(
            Painting.MOTIVE_REGISTRY,
            ModBlocks.REGISTRY,
            ModItems.REGISTRY
        ).forEach(registry -> registry.register(eventBus));
    }
}
