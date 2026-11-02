package com.theminespirit.core.intellect;

import java.util.List;
import java.util.Optional;

public record TacticalDecision(
        List<StrategicOrder> orders,
        List<StrengthVerdict> strengths,
        ActivityPlan activities,
        Optional<Expedition> expedition,
        List<String> refusals,
        List<String> notes) {
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
        if (refusals == null) {
            throw new IllegalArgumentException(
                    "Решение без списка отказов не применяется: нет отказов — пустой список");
        }
        if (notes == null) {
            throw new IllegalArgumentException(
                    "Решение без записей о границе не применяется: нет записей — пустой список");
        }
        orders = List.copyOf(orders);
        strengths = List.copyOf(strengths);
        refusals = List.copyOf(refusals);
        notes = List.copyOf(notes);
    }

    public TacticalDecision(
            List<StrategicOrder> orders,
            List<StrengthVerdict> strengths,
            ActivityPlan activities,
            Optional<Expedition> expedition,
            List<String> refusals) {
        this(orders, strengths, activities, expedition, refusals, List.of());
    }

    public TacticalDecision(
            List<StrategicOrder> orders,
            List<StrengthVerdict> strengths,
            ActivityPlan activities,
            Optional<Expedition> expedition) {
        this(orders, strengths, activities, expedition, List.of());
    }
}
