package com.theminespirit.core.engine;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public sealed interface TaskProgress {
    record SequenceAt(int index, @Nullable TaskProgress child) implements TaskProgress {
        public SequenceAt {
            requireNonNegative(index);
        }
    }

    record RepeatAt(int index, @Nullable TaskProgress child) implements TaskProgress {
        public RepeatAt {
            requireNonNegative(index);
        }
    }

    record FallbackAt(
            int index,
            @Nullable String lastReason,
            @Nullable TaskProgress child) implements TaskProgress {
        public FallbackAt {
            requireNonNegative(index);
        }
    }

    record ParallelAt(List<ParallelChild> children) implements TaskProgress {
        public ParallelAt {
            Objects.requireNonNull(children, "children не может быть null: статусы шагов parallel задают списком");
            children = List.copyOf(children);
        }
    }

    record ConditionAt(ConditionBranch branch, @Nullable TaskProgress child) implements TaskProgress {
        public ConditionAt {
            Objects.requireNonNull(branch, "branch не может быть null: по нему условие решает, какую ветку продолжать");
        }
    }

    record ScriptAt(Map<String, Object> state) implements TaskProgress {
        public ScriptAt {
            Objects.requireNonNull(state, "state не может быть null: пустое состояние задают пустой картой");
            state = Map.copyOf(state);
        }
    }

    record ParallelChild(
            ScriptStatus status,
            @Nullable String reason,
            @Nullable TaskProgress child) {
        public ParallelChild {
            Objects.requireNonNull(status, "status не может быть null: без статуса потомок parallel не восстановится");
        }
    }

    enum ConditionBranch {
        UNRESOLVED,
        THEN,
        ELSE
    }

    private static void requireNonNegative(int index) {
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Индекс прогресса не может быть отрицательным: позиция шага в узле " + "нумеруется с нуля");
        }
    }
}
