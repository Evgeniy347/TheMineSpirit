package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphSiteFactsTests {
    @Test
    void terrain_Empty_IsFlatWithoutPits() {
        assertTrue(
                GraphSiteFacts.Terrain.empty().flat(),
                "Пустой рельеф ровный: ям, висящего и следов нет"
                        + " Иначе тест terrain_Empty_IsFlatWithoutPits перестанет ловить эту поломку. При смене terrain_Empty_IsFlatWithoutPits проверь это утверждение.");
    }

    @Test
    void stageReqs_MutableMinimum_IsCopied() {
        Map<String, Integer> minimum = new HashMap<>(Map.of("hut", 1));

        GraphSiteFacts.StageReqs reqs = new GraphSiteFacts.StageReqs(minimum, Map.of("hut", 0));
        minimum.put("hut", 2);

        assertEquals(
                Map.of("hut", 1),
                reqs.minimum(),
                "Минимум этапа копируется: правка карты не меняет факт"
                        + " Иначе тест stageReqs_MutableMinimum_IsCopied перестанет ловить эту поломку. При смене stageReqs_MutableMinimum_IsCopied проверь это утверждение.");
    }
}
