package com.theminespirit.core.movement;

import com.theminespirit.core.mining.BreakTiming;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ToolBreakCost {
    public static final int APPROACH_TICKS = 10;
    public static final int CHEAP_PLACE_TICKS = 5;
    public static final int EXPENSIVE_PLACE_TICKS = 20;

    // Порядок расходных блоков: земля и камень первыми. Засыпка и мосты берут первый
    // доступный по этому порядку, поэтому он же задаёт детерминированный выбор материала.
    public static final List<String> CHEAP_ORDER = List.of(
            "dirt", "cobblestone", "gravel", "sand",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks",
            "dark_oak_planks", "mangrove_planks", "cherry_planks", "bamboo_planks",
            "crimson_planks", "warped_planks");
    public static final Set<String> THROWAWAY = Set.copyOf(CHEAP_ORDER);

    private ToolBreakCost() {
    }

    public static int breakTicks(double hardness, double toolSpeed, boolean dropsAllowed) {
        int timing = BreakTiming.ticks(hardness, toolSpeed, dropsAllowed);
        if (timing >= Integer.MAX_VALUE - APPROACH_TICKS) {
            return Integer.MAX_VALUE;
        }
        return timing + APPROACH_TICKS;
    }

    public static int placePenalty(String blockId) {
        return isThrowaway(blockId) ? CHEAP_PLACE_TICKS : EXPENSIVE_PLACE_TICKS;
    }

    public static boolean isThrowaway(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("Id блока пустой: без него неизвестно, расходный ли блок для засыпки");
        }
        String bare = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        return THROWAWAY.contains(bare) || bare.endsWith("_planks");
    }

    public static List<String> orderByCost(Map<String, Integer> costByBlock) {
        Objects.requireNonNull(costByBlock, "Карта стоимостей не может быть null: без неё неизвестно, что упорядочивать");
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(costByBlock.entrySet());
        entries.sort(Comparator.comparing(Map.Entry<String, Integer>::getValue)
                .thenComparing(Map.Entry::getKey));
        return entries.stream().map(Map.Entry::getKey).toList();
    }
}
