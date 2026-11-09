package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SelfRescueTests {
    private static final BlockCoord FEET = new BlockCoord(-188, 67, -516);

    @Test
    void mayBreakAtFeet_BlockInsideSpirit_Allowed() {
        assertTrue(
                SelfRescue.mayBreakAtFeet(FEET, FEET),
                "Блок, внутри которого застрял дух, ломается при спасении: иначе забор из чёрного списка "
                        + "оставляет его замурованным навсегда");
    }

    @Test
    void mayBreakAtFeet_NeighbourBlock_NotAllowed() {
        assertFalse(
                SelfRescue.mayBreakAtFeet(new BlockCoord(-187, 67, -516), FEET),
                "Исключение только для клетки под духом: соседние блоки постройки защищены как прежде");
    }

    @Test
    void mayBreakEscaping_LiveObserve25PlanCells_AllowedToLeavePit() throws IOException {
        JsonNode live = new ObjectMapper()
                .readTree(Files.readString(
                        Path.of("src/test/resources/live/observe25/rescue.json"), StandardCharsets.UTF_8));

        for (String name : new String[] {"spirit3", "spirit6"}) {
            JsonNode trapped = live.get(name);
            BlockCoord feet = coord(trapped.get("feet"));
            BlockCoord plan = coord(trapped.get("plan_block"));
            assertTrue(
                    SelfRescue.mayBreakEscaping(plan, feet),
                    name + " observe25 стоял в яме внутри плана "
                            + trapped.get("plan").asText()
                            + " и не мог выбраться: " + trapped.get("block").asText() + " в " + plan
                            + " — его клетка выхода, разбор разрешён с возвратом блока");
        }
    }

    @Test
    void mayBreakEscaping_CellAboveHead_Allowed() {
        BlockCoord aboveHead = new BlockCoord(FEET.x(), FEET.y() + SelfRescue.ESCAPE_HEIGHT, FEET.z());

        assertTrue(
                SelfRescue.mayBreakEscaping(aboveHead, FEET),
                "клетку над головой (" + aboveHead + ") разбирают при выходе: иначе дух в яме не распрямится "
                        + "и не шагнёт на край (observe25 B2)");
    }

    @Test
    void mayBreakEscaping_CellAbovePocket_NotAllowed() {
        BlockCoord abovePocket = new BlockCoord(FEET.x(), FEET.y() + SelfRescue.ESCAPE_HEIGHT + 1, FEET.z());

        assertFalse(
                SelfRescue.mayBreakEscaping(abovePocket, FEET),
                "выше клетки над головой (" + abovePocket + ") карман выхода кончается: это уже блок плана, "
                        + "его не трогают, иначе спасение разбирает постройку");
    }

    @Test
    void mayBreakEscaping_TwoCellsAhead_NotAllowed() {
        BlockCoord twoAhead = new BlockCoord(FEET.x() + 2, FEET.y() + 1, FEET.z());

        assertFalse(
                SelfRescue.mayBreakEscaping(twoAhead, FEET),
                "Карман выхода не растягивается на всю постройку: дальше двух клеток от духа план защищён как прежде");
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
