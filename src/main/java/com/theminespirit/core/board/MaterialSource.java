package com.theminespirit.core.board;

import com.theminespirit.core.planner.Ingredient;
import java.util.List;
import java.util.Map;

public interface MaterialSource {
    int outputCount(String item);

    List<Ingredient> ingredientsFor(String item);

    Map<String, Integer> schematicBlocks(String file);
}
