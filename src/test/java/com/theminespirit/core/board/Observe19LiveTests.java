package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.mining.LeftOreRules;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Observe19LiveTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode live() {
        try (InputStream in = Observe19LiveTests.class.getResourceAsStream("/live/observe19-h/scenario.json")) {
            if (in == null) {
                throw new IllegalStateException("Нет фикстуры наблюдения 19 H: /live/observe19-h/scenario.json");
            }
            return MAPPER.readTree(in);
        } catch (IOException error) {
            throw new IllegalStateException("Фикстура наблюдения 19 H не читается", error);
        }
    }

    @Test
    void liveObserve19_PickaxeMarker_NotTurnedIntoGatherGoal() {
        JsonNode root = live();
        String marker = root.path("pickaxeItem").asText();
        String ordinary = root.path("ordinaryItem").asText();
        int count = root.path("ordinaryCount").asInt();

        assertTrue(
                LeftOreRules.isToolTier(marker),
                "Фикстура наблюдения 19: '" + marker + "' — требование кирки, из-за которого провалилась "
                        + root.path("pickaxeSupplyTask").asText() + " ("
                        + root.path("pickaxeError").asText() + ")");

        SupplyGate.Decision onlyMarker =
                SupplyGate.decide(Map.of(marker, 1), Map.of(), 0, Map.of(), 0, BoardTuning.FAIL_BACKOFF_TICKS);

        assertTrue(
                onlyMarker.supply().isEmpty() && onlyMarker.gather().isEmpty(),
                "Маркер кирки не порождает ни снабжение, ни добычу: иначе дух снова ищет способ добычи "
                        + "несуществующего предмета");
        assertEquals(
                List.of(new SupplyGate.Request(ordinary, count)),
                SupplyGate.decide(
                                Map.of(marker, 1, ordinary, count),
                                Map.of(),
                                0,
                                Map.of(),
                                0,
                                BoardTuning.FAIL_BACKOFF_TICKS)
                        .gather(),
                "Обычный материал рядом с маркером добывается как раньше — фильтр не глушит всю нехватку");
    }
}
