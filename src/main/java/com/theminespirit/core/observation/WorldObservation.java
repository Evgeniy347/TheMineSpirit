package com.theminespirit.core.observation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WorldObservation(
        String botId,
        long tick,
        BlockCoord position,
        List<ItemCount> inventory,
        List<ItemCount> blocks,
        List<EntityRef> entities,
        List<ContainerRef> containers,
        int radius) {

    public WorldObservation {
        Objects.requireNonNull(botId, "botId не может быть null: наблюдение принадлежит конкретному духу");
        Objects.requireNonNull(position, "position не может быть null: наблюдение без позиции духа бессмысленно");
        inventory = normalizeInventory(inventory);
        blocks = normalizeInventory(blocks);
        entities = sortEntities(entities);
        containers = sortContainers(containers);
    }

    public String hash() {
        return ObservationHash.compute(this);
    }

    public String toJson() {
        return ObservationJson.toJson(this);
    }

    public static WorldObservation fromJson(String json) {
        return ObservationJson.fromJson(json);
    }

    private static List<ItemCount> normalizeInventory(List<ItemCount> entries) {
        List<ItemCount> input = requireList(entries, "inventory/blocks");
        Map<String, Integer> aggregated = new LinkedHashMap<>();
        for (ItemCount entry : input) {
            aggregated.merge(entry.item(), entry.count(), Integer::sum);
        }
        List<ItemCount> result = new ArrayList<>(aggregated.size());
        aggregated.forEach((item, count) -> result.add(new ItemCount(item, count)));
        result.sort(Comparator.comparing(ItemCount::item));
        return List.copyOf(result);
    }

    private static List<EntityRef> sortEntities(List<EntityRef> entries) {
        List<EntityRef> copy = new ArrayList<>(requireList(entries, "entities"));
        copy.sort(Comparator.comparing(EntityRef::type)
                .thenComparing(EntityRef::pos)
                .thenComparing(e -> e.id() == null ? "" : e.id())
                .thenComparingDouble(EntityRef::distance));
        return List.copyOf(copy);
    }

    private static List<ContainerRef> sortContainers(List<ContainerRef> entries) {
        List<ContainerRef> copy = new ArrayList<>(requireList(entries, "containers"));
        copy.sort(Comparator.comparing(ContainerRef::type)
                .thenComparing(ContainerRef::pos)
                .thenComparing(c -> c.id() == null ? "" : c.id()));
        return List.copyOf(copy);
    }

    private static <T> List<T> requireList(List<T> entries, String field) {
        Objects.requireNonNull(entries, field + " не может быть null: пустой список передаётся как список без элементов");
        for (T entry : entries) {
            Objects.requireNonNull(entry, field + " не может содержать null: список описывает только существующие объекты");
        }
        return entries;
    }
}
