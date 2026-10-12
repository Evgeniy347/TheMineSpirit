package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Objects;

public final class KitRule {
    private final List<String> match;
    private final String category;
    private final IdFilter filter;
    private final int keep;
    private final KitMode mode;

    KitRule(List<String> match, String category, int keep, KitMode mode) {
        Objects.requireNonNull(mode, "Режим правила набора не может быть null: по режиму решают, "
                + "оставлять лучший предмет или любое количество");
        if (keep < 0) {
            throw new IllegalArgumentException("Квота правила набора " + keep
                    + " отрицательна: квота «сколько оставить» не может быть меньше нуля");
        }
        if (match == null && category == null) {
            throw new IllegalArgumentException("У правила набора нет ни match, ни category: "
                    + "правило без шаблонов не описывает предметы");
        }
        if (match != null && category != null) {
            throw new IllegalArgumentException("У правила набора заданы и match, и category: "
                    + "два способа выбора предметов делают квоту неопределённой");
        }
        this.match = match == null ? List.of() : List.copyOf(match);
        if (match != null && this.match.isEmpty()) {
            throw new IllegalArgumentException("У правила набора пустой match: "
                    + "правило без шаблонов не описывает предметы");
        }
        if (category != null && category.isBlank()) {
            throw new IllegalArgumentException("Категория правила набора пуста: "
                    + "имя категории берётся из файла ценностей");
        }
        this.category = category;
        this.filter = this.match.isEmpty() ? null : IdFilter.of(this.match);
        this.keep = keep;
        this.mode = mode;
    }

    public List<String> match() {
        return match;
    }

    public String category() {
        return category;
    }

    public int keep() {
        return keep;
    }

    public KitMode mode() {
        return mode;
    }

    public boolean covers(String item, ValueTable values) {
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: "
                + "по ней правило набора находит категорию предмета");
        if (category != null) {
            return values.categoryOf(item).equals(category);
        }
        return filter.matches(item);
    }
}
