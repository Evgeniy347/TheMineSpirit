package com.theminespirit.core.perception;

import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import java.util.List;
import java.util.Objects;

public record PerceptionResult(
        PerceptionType type,
        List<ItemCount> blocks,
        List<EntityRef> entities,
        List<HazardRef> hazards,
        ScanRadius radius) {
    public PerceptionResult {
        Objects.requireNonNull(type, "type не может быть null: результат обязан назвать тип запроса");
        Objects.requireNonNull(radius, "radius не может быть null: результат помнит радиус запроса");
        blocks = copyWithoutNulls(blocks, "blocks");
        entities = copyWithoutNulls(entities, "entities");
        hazards = copyWithoutNulls(hazards, "hazards");
        requireEmptyUnless(type, PerceptionType.BLOCKS, "blocks", blocks);
        requireEmptyUnless(type, PerceptionType.ENTITIES, "entities", entities);
        requireEmptyUnless(type, PerceptionType.HAZARDS, "hazards", hazards);
    }

    public static PerceptionResult blocks(ScanRadius radius, List<ItemCount> blocks) {
        return new PerceptionResult(PerceptionType.BLOCKS, blocks, List.of(), List.of(), radius);
    }

    public static PerceptionResult entities(ScanRadius radius, List<EntityRef> entities) {
        return new PerceptionResult(PerceptionType.ENTITIES, List.of(), entities, List.of(), radius);
    }

    public static PerceptionResult hazards(ScanRadius radius, List<HazardRef> hazards) {
        return new PerceptionResult(PerceptionType.HAZARDS, List.of(), List.of(), hazards, radius);
    }

    private static <T> List<T> copyWithoutNulls(List<T> source, String name) {
        Objects.requireNonNull(source, name + " не может быть null: пустой результат — пустой список");
        for (T element : source) {
            Objects.requireNonNull(
                    element, "Список " + name + " содержит null: запись результата обязана существовать");
        }
        return List.copyOf(source);
    }

    private static void requireEmptyUnless(PerceptionType actual, PerceptionType owner, String name, List<?> list) {
        if (actual != owner && !list.isEmpty()) {
            throw new IllegalArgumentException("Результат типа " + actual + " содержит непустой список " + name
                    + " (" + list.size() + " шт.): " + name + " принадлежит только результату " + owner
                    + ", смесь типов скрывает, на какой запрос отвечали");
        }
    }
}
