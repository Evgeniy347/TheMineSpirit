package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Objects;

public final class ValueRule {
    private final List<String> match;
    private final int value;
    private final IdFilter filter;

    public ValueRule(List<String> match, int value) {
        Objects.requireNonNull(match,
                "Список шаблонов правила не может быть null: правило без шаблонов не описывает предметы");
        this.match = List.copyOf(match);
        if (this.match.isEmpty()) {
            throw new IllegalArgumentException(
                    "У правила ценности нет шаблонов: правило без шаблонов никогда не сработает");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Ценность правила " + this.match + " равна " + value
                    + ": отрицательная ценность делает выбор «самое дешёвое» бессмысленным");
        }
        if (value > ValueTable.VALUE_LIMIT) {
            throw new IllegalArgumentException("Ценность правила " + this.match + " равна " + value + " и выше предела "
                    + ValueTable.VALUE_LIMIT + ": такое значение выбивается из шкалы и ломает сравнение стопок");
        }
        this.value = value;
        this.filter = IdFilter.of(this.match);
    }

    public List<String> match() {
        return match;
    }

    public int value() {
        return value;
    }

    public boolean covers(String item) {
        return filter.matches(item);
    }
}
