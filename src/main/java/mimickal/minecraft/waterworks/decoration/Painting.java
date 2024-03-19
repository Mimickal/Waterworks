/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.waterworks.decoration;

import mimickal.minecraft.waterworks.Waterworks;
import net.minecraft.world.entity.decoration.Motive;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Painting {
    public static final DeferredRegister<Motive> MOTIVE_REGISTRY =
        DeferredRegister.create(ForgeRegistries.PAINTING_TYPES, Waterworks.MOD_NAME);

    public static final RegistryObject<Motive> LEVIATHAN =
        MOTIVE_REGISTRY.register("leviathan", () -> new Motive(32, 32));
}
