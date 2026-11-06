package com.theminespirit.neoforge;

import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.plan.TaskNode;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class BuildToolRestock {
    record Answer(@Nullable TaskNode plan, String reason) {}

    private final Set<String> tried = new HashSet<>();

    Answer answer(SpiritEntity spirit, String blockId, @Nullable String refusal) {
        if (tried.add(blockId)) {
            Optional<TaskNode> plan = PathTrample.toolRestock(spirit, blockId);
            if (plan.isPresent()) {
                return new Answer(plan.orElseThrow(), "");
            }
        }
        return new Answer(null, reason(spirit, blockId, refusal));
    }

    private static String reason(SpiritEntity spirit, String blockId, @Nullable String refusal) {
        String detail = refusal == null || refusal.isBlank() ? "" : "; отказ: " + refusal;
        if (!PathTrample.hasTool(spirit, blockId)) {
            return "для '" + blockId + "' нужен инструмент " + SchematicActions.toolKind(blockId)
                    + ": в сундуках нет — крафт ставит граф" + detail;
        }
        return "действие над землёй '" + blockId + "' не вышло" + detail;
    }
}
