package com.theminespirit.core.perception;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import java.util.Objects;

public record PerceptionQuery(PerceptionType type, IdFilter filter, ScanRadius radius) {
    public PerceptionQuery {
        Objects.requireNonNull(type, "type не может быть null: запрос восприятия обязан назвать, что воспринимать");
        Objects.requireNonNull(filter, "filter не может быть null: «всё» задаётся IdFilter.matchAll()");
        Objects.requireNonNull(radius, "radius не может быть null: радиус ограничивает стоимость сканирования");
        if (type == PerceptionType.HAZARDS && !filter.matchesAll()) {
            throw new IllegalArgumentException("Фильтр " + filter.patterns() + " задан для запроса опасностей: "
                    + "опасности (лава, вода, обрыв) фильтром id не отбираются, оставьте фильтр «всё»");
        }
    }

    public static PerceptionQuery blocks(IdFilter filter, ScanRadius radius) {
        return new PerceptionQuery(PerceptionType.BLOCKS, filter, radius);
    }

    public static PerceptionQuery entities(IdFilter filter, ScanRadius radius) {
        return new PerceptionQuery(PerceptionType.ENTITIES, filter, radius);
    }

    public static PerceptionQuery hazards(ScanRadius radius) {
        return new PerceptionQuery(PerceptionType.HAZARDS, IdFilter.matchAll(), radius);
    }
}
