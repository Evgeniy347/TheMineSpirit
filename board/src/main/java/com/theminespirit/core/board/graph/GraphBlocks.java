package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.GraphBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class GraphBlocks {
    private static final String BUILDING = "building:";
    private static final String TOOL = "tool:";
    private static final char AT = '@';

    interface Sink {
        void push(@Nullable String from, String node, double amount);
    }

    private final SupplyGraph graph;
    private final GraphState state;
    private final GraphFlow flow;
    private final Sink sink;
    private final Map<String, GraphPlan.Notice> notices;
    private final Map<String, String> blocked = new LinkedHashMap<>();

    GraphBlocks(SupplyGraph graph, GraphState state, GraphFlow flow, Sink sink, Map<String, GraphPlan.Notice> notices) {
        this.graph = graph;
        this.state = state;
        this.flow = flow;
        this.sink = sink;
        this.notices = notices;
    }

    void apply() {
        if (state.blocks().isEmpty()) {
            return;
        }
        Map<String, Double> scores = flow.solve();
        for (GraphBlock block : state.blocks()) {
            if (satisfied(block.need())) {
                continue;
            }
            String reason = text(block.need());
            blocked.put(block.node(), reason);
            if (block.need() instanceof GraphBlock.Impossible impossible) {
                notice(impossible.node(), "невозможно: " + reason);
                continue;
            }
            if (block.need() instanceof GraphBlock.Item item
                    && !graph.isGroup(item.item())
                    && graph.producers(item.item()).isEmpty()) {
                String missing = "нет источника " + item.item() + " в данных графа: нужда " + item.count()
                        + " не снимется ни добычей, ни крафтом";
                blocked.put(block.node(), missing);
                notice(block.node(), "невозможно: " + missing);
                continue;
            }
            pushNeed(
                    block.node(),
                    block.need(),
                    Math.max(1.0, scores.getOrDefault(block.node(), declared(block.node()))));
        }
    }

    @Nullable
    String reason(String key) {
        int at = key.indexOf(AT);
        String base = at < 0 ? key : key.substring(0, at);
        String reason = blocked.get(key);
        return reason != null ? reason : blocked.get(base);
    }

    static GraphPlan.Task blockedTask(GraphPlan.Task task, String reason) {
        return new GraphPlan.Task(
                task.id(),
                task.boardCase(),
                task.makes(),
                task.spirit(),
                task.priority(),
                task.consumes(),
                false,
                task.chance(),
                task.implemented(),
                task.ticks(),
                reason);
    }

    private boolean satisfied(GraphBlock.Need need) {
        if (need instanceof GraphBlock.Item item) {
            return count(item.item()) >= item.count();
        }
        if (need instanceof GraphBlock.Tool tool) {
            if (tool.spirit().isEmpty()) {
                return state.bestTier(tool.kind()) >= tool.rank();
            }
            return state.tier(tool.spirit(), tool.kind()) >= tool.rank();
        }
        if (need instanceof GraphBlock.Building building) {
            return state.built(building.id()) == GraphState.Built.BUILT;
        }
        if (need instanceof GraphBlock.Holder holder) {
            GraphFacts.Spirit known = state.facts().spirits().get(holder.spirit());
            if (known == null) {
                return true;
            }
            return holder.stuck() ? !known.stuck() : known.activity() == null;
        }
        return false;
    }

    private void pushNeed(String from, GraphBlock.Need need, double amount) {
        if (need instanceof GraphBlock.Item item) {
            if (!item.item().equals(from)) {
                sink.push(from, item.item(), amount);
            }
            return;
        }
        if (need instanceof GraphBlock.Tool tool) {
            if (tool.spirit().isEmpty()) {
                pushTool(from, tool.kind(), amount);
            } else {
                sink.push(from, TOOL + tool.kind() + AT + tool.spirit(), amount);
            }
            return;
        }
        if (need instanceof GraphBlock.Building building) {
            String node = BUILDING + building.id();
            if (!node.equals(from)) {
                sink.push(from, node, amount);
            }
            return;
        }
        if (need instanceof GraphBlock.Holder holder && holder.stuck()) {
            sink.push(from, DigOperations.RESCUE, amount);
        }
    }

    private void pushTool(String from, String kind, double amount) {
        for (String spirit : state.kits().keySet()) {
            sink.push(from, TOOL + kind + AT + spirit, amount / Math.max(1, state.spirits()));
        }
    }

    private double declared(String node) {
        if (node.startsWith(BUILDING)) {
            SupplyGraph.Building building = graph.buildings().get(node.substring(BUILDING.length()));
            return building == null ? 1 : Math.max(1, building.base());
        }
        SupplyGraph.Item item = graph.items().get(node);
        if (item != null) {
            return Math.max(1, item.base());
        }
        return Math.max(1, graph.maxPriority() / 2.0);
    }

    private int count(String node) {
        return GraphLack.count(graph, state, node);
    }

    private void notice(String node, String reason) {
        notices.putIfAbsent(node, new GraphPlan.Notice(node, reason));
    }

    private static String text(GraphBlock.Need need) {
        if (need instanceof GraphBlock.Item item) {
            return "нужно " + item.item() + " ×" + item.count();
        }
        if (need instanceof GraphBlock.Tool tool) {
            return "нужен инструмент " + tool.kind() + " тира " + tool.rank()
                    + (tool.spirit().isEmpty() ? "" : " у " + tool.spirit());
        }
        if (need instanceof GraphBlock.Building building) {
            return "нужна постройка " + building.id();
        }
        if (need instanceof GraphBlock.Path path) {
            return "нужен путь к " + path.point();
        }
        if (need instanceof GraphBlock.Holder holder) {
            return (holder.stuck() ? "застрял держатель " : "занят держатель ") + holder.spirit();
        }
        if (need instanceof GraphBlock.Impossible impossible) {
            return "нет источника " + impossible.node();
        }
        return "блокировка без причины";
    }
}
