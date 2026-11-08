package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SettlementTreeLayout {
    record Branch(List<String> chain) {
        Branch {
            chain = List.copyOf(Objects.requireNonNull(
                    chain, "Ветка древа не может быть null: пустую ветку задают пустым списком"));
            if (chain.isEmpty()) {
                throw new IllegalArgumentException("Ветка древа без объектов: от корня ведёт хотя бы одна рамка");
            }
        }
    }

    record Result(Map<String, SettlementTree.Point> places, List<SettlementTree.Edge> edges, int width, int height) {
        Result {
            places = Map.copyOf(Objects.requireNonNull(places, "Места рамок не могут быть null"));
            edges = List.copyOf(Objects.requireNonNull(edges, "Связи древа не могут быть null"));
        }
    }

    private SettlementTreeLayout() {}

    static Result of(String rootId, List<Branch> branches, List<String> gateParents, String gateId) {
        Objects.requireNonNull(rootId, "id корня не может быть null: с него начинается древо");
        Objects.requireNonNull(gateId, "id ворот этапа не может быть null: ими заканчивается ветка");
        List<Branch> ordered = List.copyOf(
                Objects.requireNonNull(branches, "Ветки древа не могут быть null: без них — пустой список"));
        List<String> parents = List.copyOf(
                Objects.requireNonNull(gateParents, "Родители ворот не могут быть null: пустые — пустой список"));
        Map<String, SettlementTree.Point> places = new LinkedHashMap<>();
        int rootRow = ordered.isEmpty() ? 0 : (ordered.size() - 1) / 2;
        places.put(rootId, new SettlementTree.Point(0, rowY(rootRow)));
        int maxChain = 0;
        for (int row = 0; row < ordered.size(); row++) {
            Branch branch = ordered.get(row);
            maxChain = Math.max(maxChain, branch.chain().size());
            for (int column = 0; column < branch.chain().size(); column++) {
                places.put(branch.chain().get(column), new SettlementTree.Point(columnX(column + 1), rowY(row)));
            }
        }
        int gateColumn = maxChain + 1;
        places.put(gateId, new SettlementTree.Point(columnX(gateColumn), rowY(ordered.size())));
        List<SettlementTree.Edge> edges = new ArrayList<>();
        for (Branch branch : ordered) {
            edges.add(edge(places, rootId, branch.chain().get(0)));
            for (int index = 1; index < branch.chain().size(); index++) {
                edges.add(edge(
                        places, branch.chain().get(index - 1), branch.chain().get(index)));
            }
        }
        for (String parent : parents) {
            edges.add(gateEdge(places, parent, gateId));
        }
        int width = 0;
        int height = 0;
        for (SettlementTree.Point place : places.values()) {
            width = Math.max(width, place.x());
            height = Math.max(height, place.y());
        }
        return new Result(places, edges, width + SettlementTreeStyle.NODE, height + SettlementTreeStyle.NODE);
    }

    private static int columnX(int column) {
        return column * SettlementTreeStyle.STEP_X;
    }

    private static int rowY(int row) {
        return row * SettlementTreeStyle.STEP_Y;
    }

    private static SettlementTree.Edge edge(Map<String, SettlementTree.Point> places, String fromId, String toId) {
        SettlementTree.Point from = required(places, fromId);
        SettlementTree.Point to = required(places, toId);
        SettlementTree.Point start = rightCenter(from);
        SettlementTree.Point end = leftCenter(to);
        if (start.y() == end.y()) {
            return new SettlementTree.Edge(fromId, toId, List.of(start, end));
        }
        int middle = (start.x() + end.x()) / 2;
        return new SettlementTree.Edge(
                fromId,
                toId,
                List.of(
                        start,
                        new SettlementTree.Point(middle, start.y()),
                        new SettlementTree.Point(middle, end.y()),
                        end));
    }

    private static SettlementTree.Edge gateEdge(
            Map<String, SettlementTree.Point> places, String fromId, String gateId) {
        SettlementTree.Point from = required(places, fromId);
        SettlementTree.Point gate = required(places, gateId);
        SettlementTree.Point start = rightCenter(from);
        SettlementTree.Point end = leftCenter(gate);
        int stub = SettlementTreeStyle.GATE_STUB;
        return new SettlementTree.Edge(
                fromId,
                gateId,
                List.of(
                        start,
                        new SettlementTree.Point(stub, start.y()),
                        new SettlementTree.Point(stub, end.y()),
                        end));
    }

    private static SettlementTree.Point rightCenter(SettlementTree.Point place) {
        return new SettlementTree.Point(place.x() + SettlementTreeStyle.NODE, place.y() + SettlementTreeStyle.NODE / 2);
    }

    private static SettlementTree.Point leftCenter(SettlementTree.Point place) {
        return new SettlementTree.Point(place.x(), place.y() + SettlementTreeStyle.NODE / 2);
    }

    private static SettlementTree.Point required(Map<String, SettlementTree.Point> places, String id) {
        SettlementTree.Point place = places.get(id);
        if (place == null) {
            throw new IllegalStateException("Узел '" + id + "' не разложен: связь ведёт в рамку без места на древе");
        }
        return place;
    }
}
