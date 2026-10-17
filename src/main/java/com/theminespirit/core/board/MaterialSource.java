package com.theminespirit.core.board;

import com.theminespirit.core.planner.Ingredient;
import java.util.List;
import java.util.Map;

/**
 * Знания о рецептах и схемах для гейта материалов: адаптер отдаёт их из игры, ядро
 * считает по ним нужду кейса. Без источника ни рецепт, ни схему не прочитать.
 */
public interface MaterialSource {
    int outputCount(String item);

    List<Ingredient> ingredientsFor(String item);

    Map<String, Integer> schematicBlocks(String file);
}
