package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.List;

final class GraphMinimums {
    private static final int BOOST = 35;
    private static final char AT = '@';
    private static final String TOOL = "tool:";

    record Root(String node, double amount) {}

    record Result(List<Root> roots, List<GraphPlan.Notice> notices) {
        Result {
            roots = List.copyOf(roots);
            notices = List.copyOf(notices);
        }
    }

    private GraphMinimums() {}

    static Result of(SupplyGraph graph, GraphState state) {
        GraphFacts.StageReqs reqs = state.facts().stageReqs();
        List<Root> roots = new ArrayList<>();
        List<GraphPlan.Notice> notices = new ArrayList<>();
        reqs.minimum().forEach((node, need) -> {
            int have = reqs.actual().getOrDefault(node, 0);
            if (have >= need) {
                return;
            }
            if (graph.isGroup(node)) {
                roots.add(new Root(node, BOOST));
            } else if (graph.isTool(node)) {
                int split = node.lastIndexOf('_');
                String tier = node.substring(0, split);
                String kind = node.substring(split + 1);
                int rank = graph.rank(tier);
                if (rank < 0) {
                    notices.add(new GraphPlan.Notice(
                            node,
                            "минимум просит неизвестный уровень инструмента " + node + ": добавьте уровень в tiers"));
                    return;
                }
                for (String spirit : state.kits().keySet()) {
                    if (state.tier(spirit, kind) < rank) {
                        roots.add(new Root(TOOL + kind + AT + spirit, BOOST));
                    }
                }
            } else if (graph.items().containsKey(node)) {
                roots.add(new Root(node, graph.items().get(node).base()));
            } else {
                notices.add(new GraphPlan.Notice(
                        node,
                        "минимум просит узел " + node
                                + ", которого нет в графе: добавьте предмет, группу или инструмент"));
            }
        });
        return new Result(roots, notices);
    }
}
