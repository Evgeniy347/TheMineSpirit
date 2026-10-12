package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Objects;

public final class Category {
    private final String name;
    private final List<String> match;
    private final IdFilter filter;

    public Category(String name, List<String> match) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Категория без имени: по имени сортёр сундука расставляет группы предметов");
        }
        Objects.requireNonNull(match,
                "Список шаблонов категории не может быть null: категория без шаблонов пуста");
        this.name = name;
        this.match = List.copyOf(match);
        if (this.match.isEmpty()) {
            throw new IllegalArgumentException("У категории '" + name
                    + "' нет шаблонов: пустая категория не описывает никаких предметов");
        }
        this.filter = IdFilter.of(this.match);
    }

    public String name() {
        return name;
    }

    public List<String> match() {
        return match;
    }

    public boolean covers(String item) {
        return filter.matches(item);
    }
}
