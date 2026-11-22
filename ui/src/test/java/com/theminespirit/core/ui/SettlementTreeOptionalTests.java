package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementTreeOptionalTests {
    private static final SettlementTree TREE = new SettlementTree(
            1,
            1,
            1,
            List.of(
                    node("root", SettlementTree.Kind.ROOT, false, 0, 0),
                    node("house", SettlementTree.Kind.BUILDING, true, 1, 0),
                    node("gate", SettlementTree.Kind.GATE, true, 2, 0)),
            List.of(edge("root", "house"), edge("root", "gate")),
            400,
            200);

    @Test
    void tree_OptionalFilter_KeepsRootAndGate() {
        SettlementTree filtered = TREE.withoutOptional();
        assertEquals(
                List.of("root", "gate"),
                filtered.nodes().stream().map(SettlementTree.Node::id).toList(),
                "Фильтр оставляет корень и ворота: иначе экран без желательных объектов теряет каркас древа. При смене tree_OptionalFilter_KeepsRootAndGate проверь это утверждение.");
        assertTrue(
                filtered.nodes().stream()
                        .filter(node -> node.kind() == SettlementTree.Kind.BUILDING)
                        .noneMatch(SettlementTree.Node::optional),
                "После фильтра не остаётся желательных построек: иначе тумблер «Только обязательные» ничего не меняет. При смене tree_OptionalFilter_KeepsRootAndGate проверь это утверждение.");
    }

    @Test
    void tree_OptionalFilter_DropsDanglingEdges() {
        SettlementTree filtered = TREE.withoutOptional();
        assertEquals(
                1,
                filtered.edges().size(),
                "Связь к скрытому объекту обязана исчезнуть: иначе линия висит в пустоте и уводит взгляд с обязательной ветки. При смене tree_OptionalFilter_DropsDanglingEdges проверь это утверждение.");
        assertTrue(
                "gate".equals(filtered.edges().get(0).to()),
                "Оставшаяся связь ведёт к воротам: иначе фильтр перепутал ветки и потерял обязательный путь. При смене tree_OptionalFilter_DropsDanglingEdges проверь это утверждение.");
    }

    @Test
    void tree_OptionalFilter_IsDeterministic() {
        SettlementTree first = TREE.withoutOptional();
        SettlementTree second = TREE.withoutOptional();
        assertEquals(
                first.nodes(),
                second.nodes(),
                "Повторный фильтр даёт тот же порядок узлов: иначе древо переставляется между кадрами и клик уходит не в ту рамку. При смене tree_OptionalFilter_IsDeterministic проверь это утверждение.");
        assertEquals(
                first.edges(),
                second.edges(),
                "Повторный фильтр даёт те же связи: иначе линии между кадрами прыгают. При смене tree_OptionalFilter_IsDeterministic проверь это утверждение.");
    }

    private static SettlementTree.Node node(String id, SettlementTree.Kind kind, boolean optional, int x, int y) {
        return new SettlementTree.Node(
                id,
                kind,
                SettlementTree.State.READY,
                id,
                "minecraft:stone",
                0,
                100,
                optional,
                false,
                x,
                y,
                null,
                Map.of(),
                List.of());
    }

    private static SettlementTree.Edge edge(String from, String to) {
        return new SettlementTree.Edge(
                from, to, List.of(new SettlementTree.Point(0, 0), new SettlementTree.Point(1, 1)));
    }
}
