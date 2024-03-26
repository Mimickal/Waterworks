/****************************************************************************************
 * This file is part of Waterworks, a Minecraft mod that changes water dynamics.
 * Copyright (C) 2024 Mimickal (Mia Moretti).
 *
 * Waterworks is free software under the GNU Affero General Public License v3.0.
 * See LICENSE or <https://www.gnu.org/licenses/agpl-3.0.en.html> for more information.
 ****************************************************************************************/
package mimickal.minecraft.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class ListUtil {
    /**
     * A stream collector that shuffles the results. Based on {@link ArrayList} because it guarantees mutability.
     * Shamelessly ripped off from <a href="https://stackoverflow.com/a/36391959">StackOverflow</a>.
     */
    public static <T> Collector<T, ?, ArrayList<T>> toShuffledList() {
        return Collectors.collectingAndThen(
            Collectors.toCollection(ArrayList::new),
            list -> {
                Collections.shuffle(list);
                return list;
            }
        );
    }
}
