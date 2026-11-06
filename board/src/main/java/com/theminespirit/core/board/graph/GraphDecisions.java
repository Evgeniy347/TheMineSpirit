package com.theminespirit.core.board.graph;

import java.util.List;
import java.util.Objects;

public record GraphDecisions(long tick, List<Row> decisions) {
    public GraphDecisions {
        if (tick < 0) {
            throw new IllegalArgumentException("Тик объяснения решений " + tick + " отрицателен: время идёт вперёд");
        }
        decisions = List.copyOf(
                Objects.requireNonNull(decisions, "Список решений не может быть null: без решений — пустой список"));
    }

    public record Row(
            String spirit, String task, int weight, boolean feasible, String next, int nextWeight, String why) {
        public Row {
            Objects.requireNonNull(spirit, "Дух решения не может быть null: без духа — «любой»");
            Objects.requireNonNull(task, "Работа решения не может быть null: объяснять нечего");
            Objects.requireNonNull(next, "Следующий кандидат не может быть null: его отсутствие — пустая строка");
            Objects.requireNonNull(why, "Причина отказа не может быть null: объяснение без причины бесполезно");
            if (weight < 0 || nextWeight < 0) {
                throw new IllegalArgumentException(
                        "Вес решения " + weight + "/" + nextWeight + " отрицателен: приоритеты не бывают минусом");
            }
        }

        public String line() {
            return spirit + " → " + task + " (" + weight + ")"
                    + (next.isBlank()
                            ? ", кандидатов больше нет"
                            : ", следующий " + next + " (" + nextWeight + "): " + why);
        }
    }

    public static GraphDecisions empty() {
        return new GraphDecisions(0, List.of());
    }
}
