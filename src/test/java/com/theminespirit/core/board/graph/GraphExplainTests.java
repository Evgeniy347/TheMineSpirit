package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphExplainTests {
    @Test
    void of_ChosenAndNext_ExplainsWeightGap() {
        GraphPlan plan =
                plan(task("make:dirt", 80, true), task("chop_oak_log", 60, true), task("make:planks", 40, false));

        GraphDecisions decisions = GraphExplain.of(plan, 500);

        assertEquals(500, decisions.tick(), "Объяснение помечено тиком, на котором принято решение");
        GraphDecisions.Row first = decisions.decisions().get(0);
        assertEquals("make:dirt", first.task(), "Первым объясняется самая тяжёлая работа");
        assertEquals("chop_oak_log", first.next(), "Следующий кандидат — вторая по весу работа");
        assertTrue(first.why().contains("20"), "Причина отказа — разница веса: " + first.why());
        assertEquals("любой", first.spirit(), "Работа без духа объясняется как «любой»");
        assertTrue(
                decisions.decisions().get(1).why().contains("неосуществим"),
                "Невозможная работа объясняется материалами: "
                        + decisions.decisions().get(1).why());
    }

    @Test
    void of_SpiritBoundTask_NamesSpirit() {
        GraphPlan plan = plan(new GraphPlan.Task(
                "tool:pickaxe@spirit-1",
                BoardCase.CRAFT_WOODEN_PICKAXE,
                "tool:pickaxe",
                Optional.of("spirit-1"),
                50,
                Map.of(),
                true,
                false,
                true,
                100));

        GraphDecisions.Row row = GraphExplain.of(plan, 1).decisions().get(0);

        assertEquals("spirit-1", row.spirit(), "Личная работа объясняется духом, который её получил");
        assertEquals("", row.next(), "Если кандидатов нет, следующий пуст");
        assertTrue(row.line().contains("кандидатов больше нет"), "Строка лога говорит, что выбора больше нет");
    }

    @Test
    void row_Line_ContainsWorkWeightAndNext() {
        GraphDecisions.Row row = new GraphDecisions.Row("spirit-1", "make:dirt", 42, true, "chop", 38, "вес ниже на 4");

        assertEquals(
                "spirit-1 → make:dirt (42), следующий chop (38): вес ниже на 4",
                row.line(),
                "Формат строки [graph] решение: дух → работа (вес), следующий кандидат и почему не он");
    }

    private static GraphPlan plan(GraphPlan.Task... tasks) {
        return new GraphPlan(List.of(tasks), List.of(), List.of());
    }

    private static GraphPlan.Task task(String id, int priority, boolean feasible) {
        return new GraphPlan.Task(
                id, BoardCase.GATHER_MATERIALS, id, Optional.empty(), priority, Map.of(), feasible, false, true, 100);
    }
}
