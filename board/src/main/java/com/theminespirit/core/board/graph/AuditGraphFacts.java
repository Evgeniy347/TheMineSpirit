package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.audit.AuditGraphKeys;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.building.SchematicBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class AuditGraphFacts {
    private AuditGraphFacts() {}

    public static @Nullable Group group(GraphState state, String taskId) {
        Objects.requireNonNull(state, "Состояние графа не может быть null: группу ищут в нём");
        String key = "audit:" + taskId;
        return state.facts().auditGroups().stream()
                .filter(group -> AuditGraphKeys.key(group).equals(key))
                .findFirst()
                .orElse(null);
    }

    public static Map<String, Integer> materials(Group group) {
        Objects.requireNonNull(group, "Группа не может быть null: материалы считаются по её предметам");
        Map<String, Integer> need = new LinkedHashMap<>();
        for (DiffItem item : group.items()) {
            if (item.action() == DiffItem.Action.BREAK) {
                continue;
            }
            String block = SchematicBlocks.path(
                    SchematicBlocks.placementBlock(item.wanted().id()));
            int bracket = block.indexOf('[');
            if (bracket >= 0) {
                block = block.substring(0, bracket);
            }
            need.merge(block, 1, Integer::sum);
        }
        return Map.copyOf(need);
    }

    public static boolean ready(GraphState state, GraphEvent.Task task) {
        if (task.after() != null && !completed(state, task.after())) {
            return false;
        }
        for (String required : task.requires()) {
            if (!completed(state, required)) {
                return false;
            }
        }
        return true;
    }

    public static int weight(Group group, int base) {
        return weight(null, group, base);
    }

    public static int weight(@Nullable GraphState state, Group group, int base) {
        if (group.total() <= 0) {
            return base;
        }
        double share = (double) group.pending() / group.total();
        double importance =
                state != null && state.facts().stageReqs().minimum().containsKey(group.subject()) ? 1.5 : 1.0;
        return base + (int) Math.round(base * share * importance);
    }

    private static boolean completed(GraphState state, String prefix) {
        boolean found = false;
        for (Group group : state.facts().auditGroups()) {
            if (AuditGraphKeys.key(group).startsWith(prefix)) {
                found = true;
                if (group.status() != Group.Status.DONE) {
                    return false;
                }
            }
        }
        return found || state.world().stream().noneMatch(key -> key.startsWith(prefix));
    }
}
