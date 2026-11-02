package com.theminespirit.core.board;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StageView(int stage, int stages, Map<String, Integer> missing, List<Requirement> requirements) {
    public StageView {
        if (stage < 1 || stages < 1 || stage > stages + 1) {
            throw new IllegalArgumentException("Этап " + stage + " из " + stages
                    + ": поселение стоит на этапе от первого до «все закрыты», иначе счёт сбит");
        }
        missing = Map.copyOf(
                Objects.requireNonNull(missing, "Остаток этапа не может быть null: пустой задают пустой картой"));
        requirements = List.copyOf(Objects.requireNonNull(
                requirements, "Требования этапа не могут быть null: пустой этап задают пустым списком"));
    }

    public record Requirement(int stage, String id, int level, boolean closed, StageRollback.Readiness world) {
        public Requirement {
            if (stage < 1) {
                throw new IllegalArgumentException(
                        "Требование '" + id + "' этапа " + stage + ": этапы поселения начинаются с первого");
            }
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Требование этапа без имени: по имени видно, что не готово");
            }
            if (level < 1) {
                throw new IllegalArgumentException(
                        "Требование '" + id + "' уровня " + level + ": уровни этапа начинаются с первого");
            }
            Objects.requireNonNull(
                    world, "Состояние мира не может быть null: неизвестное — " + StageRollback.Readiness.UNKNOWN);
        }
    }
}
