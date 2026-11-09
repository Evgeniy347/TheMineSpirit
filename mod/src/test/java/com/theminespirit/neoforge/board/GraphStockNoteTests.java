package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphStockNoteTests {
    @Test
    void line_MixedAxes_ListsMaterialCounts() {
        String line = GraphStockNote.line(3, Map.of("wooden", 3, "stone", 1), Map.of("wooden", 1, "iron", 1), Map.of());

        assertEquals(
                "[graph] запасы: сундуков 3, топоров 6 (сундуки 4, духи 2, в руках 0; "
                        + "деревянных 4, каменных 1, железных 1)",
                line,
                "Запас топоров обязан разделять материал: иначе 57 деревянных и 57 каменных не отличить, "
                        + "и крафт «при запасе» выглядит загадкой (observe25 B10)");
    }

    @Test
    void line_NoAxes_KeepsSourceCounts() {
        String line = GraphStockNote.line(2, Map.of(), Map.of(), Map.of());

        assertEquals(
                "[graph] запасы: сундуков 2, топоров 0 (сундуки 0, духи 0, в руках 0)",
                line,
                "Пустой запас не выдумывает материал: строку видят в журнале каждый раз");
    }

    @Test
    void tier_AxeId_ReturnsMaterial() {
        assertEquals("wooden", GraphStockNote.tier("minecraft:wooden_axe"), "Материал берётся из id предмета");
        assertEquals("stone", GraphStockNote.tier("stone_axe"), "Пространство имён не обязательно");
    }
}
