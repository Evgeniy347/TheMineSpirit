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
        int radius,
        SpiritStatus spirit,
        List<GroundItem> groundItems,
        List<ContainerContents> containerItems,
        WorldTime time,
        List<OpenBlock> openBlocks) {

    public WorldObservation {
        Objects.requireNonNull(botId, "botId не может быть null: наблюдение принадлежит конкретному духу");
        Objects.requireNonNull(position, "position не может быть null: наблюдение без позиции духа бессмысленно");
        inventory = ItemCounts.aggregate(inventory, "inventory");
        blocks = ItemCounts.aggregate(blocks, "blocks");
        entities = sortEntities(entities);
        containers = sortContainers(containers);
        groundItems = aggregateGroundItems(groundItems);
        containerItems = sortContainerItems(containerItems);
        openBlocks = sortOpenBlocks(openBlocks);
    }

    public WorldObservation(String botId, long tick, BlockCoord position, List<ItemCount> inventory,
            List<ItemCount> blocks, List<EntityRef> entities, List<ContainerRef> containers, int radius) {
        this(botId, tick, position, inventory, blocks, entities, containers, radius,
                null, List.of(), List.of(), null, List.of());
    }

    public WorldObservation(String botId, long tick, BlockCoord position, List<ItemCount> inventory,
            List<ItemCount> blocks, List<EntityRef> entities, List<ContainerRef> containers, int radius,
            SpiritStatus spirit, List<GroundItem> groundItems, List<ContainerContents> containerItems,
            WorldTime time) {
        this(botId, tick, position, inventory, blocks, entities, containers, radius, spirit, groundItems,
                containerItems, time, List.of());
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

    private static List<GroundItem> aggregateGroundItems(List<GroundItem> entries) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (GroundItem entry : requireList(entries, "groundItems")) {
            merged.merge(entry.item(), entry.count(), Integer::sum);
        }
        List<GroundItem> result = new ArrayList<>(merged.size());
        merged.forEach((item, count) -> result.add(new GroundItem(item, count)));
        result.sort(Comparator.comparing(GroundItem::item));
        return List.copyOf(result);
    }

    private static List<OpenBlock> sortOpenBlocks(List<OpenBlock> entries) {
        List<OpenBlock> copy = new ArrayList<>(requireList(entries, "openBlocks"));
        copy.sort(OpenBlocks.ORDER);
        return List.copyOf(copy);
    }

    private static List<ContainerContents> sortContainerItems(List<ContainerContents> entries) {
        List<ContainerContents> copy = new ArrayList<>(requireList(entries, "containerItems"));
        copy.sort(Comparator.comparing(ContainerContents::pos));
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
