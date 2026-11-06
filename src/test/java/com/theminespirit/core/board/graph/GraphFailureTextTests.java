package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphFailureTextTests {
    @Test
    void line_EmptyMemory_IsEmpty() {
        assertEquals(
                "",
                GraphFailureText.line(List.of()),
                "Пустая память неудач не печатает строку: иначе лог заполняется «память неудач: » без данных");
    }

    @Test
    void line_PenaltiesDiffer_NamesTargetAndCauseBiggestFirst() {
        String line = GraphFailureText.line(List.of(
                failure("chop_tree", "-163,85,-507", GraphFeedback.Cause.NO_PATH, 2.5, 3),
                failure("mine_coal", "12,40,-7", GraphFeedback.Cause.NO_MATERIAL, 7.25, 5)));

        assertTrue(
                line.indexOf("mine_coal") >= 0 && line.indexOf("mine_coal") < line.indexOf("chop_tree"),
                "Сначала цель с большим штрафом: иначе строка прячет главную причину простоя: " + line);
        assertTrue(
                line.contains("mine_coal → 12,40,-7: NO_MATERIAL, штраф 7.25, провалов 5"),
                "Строка памяти называет узел, цель, причину, штраф и число провалов: " + line);
    }

    @Test
    void line_MoreThanShown_AddsRemainder() {
        List<GraphFeedback.Failure> failures = new ArrayList<>();
        for (int index = 0; index < GraphFailureText.SHOWN + 2; index++) {
            failures.add(failure("node" + index, "", GraphFeedback.Cause.OTHER, index, 1));
        }

        String line = GraphFailureText.line(failures);

        assertTrue(
                line.endsWith("… ещё 2"),
                "Строка не обрезает молча: после " + GraphFailureText.SHOWN + " записей видно, сколько осталось: "
                        + line);
        assertTrue(
                !line.contains("node0 "), "В строку не попадает запись с наименьшим штрафом при переполнении: " + line);
    }

    @Test
    void line_BlankTarget_ShowsDash() {
        assertTrue(
                GraphFailureText.line(List.of(failure("rescue", "", GraphFeedback.Cause.NOT_REACHED, 1, 1)))
                        .contains("rescue → -: NOT_REACHED"),
                "Пустая цель печатается прочерком, а не двойным пробелом: иначе строку не прочитать");
    }

    private static GraphFeedback.Failure failure(
            String node, String target, GraphFeedback.Cause cause, double weight, int count) {
        return new GraphFeedback.Failure(node, target, count, cause, 100, weight);
    }
}
