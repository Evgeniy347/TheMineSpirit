package com.theminespirit.core.intellect;

import java.util.List;
import java.util.Optional;

public record TacticalDecision(List<StrategicOrder> orders, List<StrengthVerdict> strengths,
        ActivityPlan activities, Optional<Expedition> expedition) {
    public TacticalDecision {
        if (orders == null) {
            throw new IllegalArgumentException("Решение без приказов не применяется: пустой список — нет приказов");
        }
        if (strengths == null) {
            throw new IllegalArgumentException("Решение без оценки сил не применяется: пустой список — нет угроз");
        }
        if (activities == null) {
            throw new IllegalArgumentException("Решение без плана деятельностей не применяется");
        }
        if (expedition == null) {
            throw new IllegalArgumentException(
                    "Решение без контейнера экспедиции не применяется: отсутствие похода — Optional.empty()");
        }
        orders = List.copyOf(orders);
        strengths = List.copyOf(strengths);
    }
}
