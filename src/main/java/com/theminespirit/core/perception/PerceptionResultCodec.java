package com.theminespirit.core.perception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class PerceptionResultCodec {
    public static final int MAX_STRING_LENGTH = 200;
    public static final int MAX_LIST_ITEMS = 32;
    public static final int MAX_JSON_BYTES = 4096;

    static final String KEY_TRUNCATED = "truncated";
    static final String KEY_TYPE = "type";
    static final String KEY_SCRIPT = "script";
    static final String KEY_RADIUS = "radius";
    static final String KEY_BLOCKS = "blocks";
    static final String KEY_ENTITIES = "entities";
    static final String KEY_HAZARDS = "hazards";
    static final String KEY_SIGHTINGS = "sightings";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PerceptionResultCodec() {
    }

    public static Map<String, Object> toMap(PerceptionResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_TYPE, result.type().name().toLowerCase(Locale.ROOT));
        map.put(KEY_RADIUS, result.radius().value());
        switch (result.type()) {
            case BLOCKS -> map.put(KEY_BLOCKS, items(result.blocks()));
            case ENTITIES -> map.put(KEY_ENTITIES, entities(result.entities()));
            case HAZARDS -> map.put(KEY_HAZARDS, hazards(result.hazards()));
        }
        return enforceLimits(map);
    }

    public static Map<String, Object> toMap(Finding finding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_TYPE, findingType(finding.script()));
        map.put(KEY_SCRIPT, finding.script());
        List<Object> sightings = new ArrayList<>();
        for (Sighting sighting : finding.sightings()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("kind", sighting.kind());
            entry.put("label", sighting.label());
            entry.put("pos", pos(sighting.pos()));
            entry.put("detail", sighting.detail());
            sightings.add(entry);
        }
        map.put(KEY_SIGHTINGS, sightings);
        return enforceLimits(map);
    }

    public static PerceptionResult toPerceptionResult(Map<String, Object> map) {
        String type = requireText(map, KEY_TYPE);
        ScanRadius radius = new ScanRadius(asInt(map.get(KEY_RADIUS), KEY_RADIUS));
        return switch (type) {
            case "blocks" -> PerceptionResult.blocks(radius, itemRecords(map.get(KEY_BLOCKS)));
            case "entities" -> PerceptionResult.entities(radius, entityRecords(map.get(KEY_ENTITIES)));
            case "hazards" -> PerceptionResult.hazards(radius, hazardRecords(map.get(KEY_HAZARDS)));
            default -> throw new IllegalArgumentException("Неизвестный тип результата восприятия '" + type
                    + "': допустимы blocks, entities, hazards");
        };
    }

    public static Finding toFinding(Map<String, Object> map) {
        String script = requireText(map, KEY_SCRIPT);
        Object raw = map.get(KEY_SIGHTINGS);
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("Поле sightings обязано быть списком, фактически: "
                    + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        List<Sighting> sightings = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("Запись sightings обязана быть JSON-объектом, фактически: "
                        + (element == null ? "null" : element.getClass().getSimpleName()));
            }
            sightings.add(new Sighting(
                    requireText(entry, "kind"),
                    requireText(entry, "label"),
                    coord(entry.get("pos"), "pos"),
                    requireText(entry, "detail")));
        }
        return new Finding(script, sightings);
    }

    static String findingType(String script) {
        if (script.startsWith("locate_")) {
            return "located";
        }
        if (CellReport.SCRIPT.equals(script)) {
            return "cell";
        }
        return "memory";
    }

    private static List<Object> items(List<ItemCount> blocks) {
        List<Object> list = new ArrayList<>();
        for (ItemCount item : blocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("item", item.item());
            entry.put("count", item.count());
            list.add(entry);
        }
        return list;
    }

    private static List<Object> entities(List<EntityRef> entities) {
        List<Object> list = new ArrayList<>();
        for (EntityRef entity : entities) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", entity.type());
            entry.put("pos", pos(entity.pos()));
            entry.put("distance", entity.distance());
            list.add(entry);
        }
        return list;
    }

    private static List<Object> hazards(List<HazardRef> hazards) {
        List<Object> list = new ArrayList<>();
        for (HazardRef hazard : hazards) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", hazard.type().name().toLowerCase(Locale.ROOT));
            entry.put("pos", pos(hazard.pos()));
            entry.put("distance", hazard.distance());
            list.add(entry);
        }
        return list;
    }

    private static List<Integer> pos(BlockCoord coord) {
        return List.of(coord.x(), coord.y(), coord.z());
    }

    private static List<ItemCount> itemRecords(Object raw) {
        List<ItemCount> out = new ArrayList<>();
        for (Object element : asList(raw, KEY_BLOCKS)) {
            Map<?, ?> entry = asObject(element, KEY_BLOCKS);
            out.add(new ItemCount(requireText(entry, "item"), asInt(entry.get("count"), "count")));
        }
        return out;
    }

    private static List<EntityRef> entityRecords(Object raw) {
        List<EntityRef> out = new ArrayList<>();
        for (Object element : asList(raw, KEY_ENTITIES)) {
            Map<?, ?> entry = asObject(element, KEY_ENTITIES);
            out.add(new EntityRef(requireText(entry, "type"), coord(entry.get("pos"), "pos"),
                    asDouble(entry.get("distance"), "distance")));
        }
        return out;
    }

    private static List<HazardRef> hazardRecords(Object raw) {
        List<HazardRef> out = new ArrayList<>();
        for (Object element : asList(raw, KEY_HAZARDS)) {
            Map<?, ?> entry = asObject(element, KEY_HAZARDS);
            out.add(new HazardRef(HazardType.valueOf(requireText(entry, "type").toUpperCase(Locale.ROOT)),
                    coord(entry.get("pos"), "pos"), asDouble(entry.get("distance"), "distance")));
        }
        return out;
    }

    private static List<?> asList(Object raw, String field) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("Поле " + field + " обязано быть списком, фактически: "
                    + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        return list;
    }

    private static Map<?, ?> asObject(Object raw, String field) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Запись поля " + field + " обязана быть JSON-объектом, фактически: "
                    + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        return map;
    }

    private static String requireText(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Поле " + field + " обязано быть непустой строкой, фактически: "
                    + (value == null ? "null" : value));
        }
        return text;
    }

    private static int asInt(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Поле " + field + " обязано быть числом, фактически: "
                    + (value == null ? "null" : value));
        }
        return number.intValue();
    }

    private static double asDouble(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Поле " + field + " обязано быть числом, фактически: "
                    + (value == null ? "null" : value));
        }
        return number.doubleValue();
    }

    private static BlockCoord coord(Object raw, String field) {
        List<?> list = asList(raw, field);
        if (list.size() != 3) {
            throw new IllegalArgumentException("Поле " + field + " обязано иметь три координаты, фактически: "
                    + list.size());
        }
        return new BlockCoord(asInt(list.get(0), field + "[0]"), asInt(list.get(1), field + "[1]"),
                asInt(list.get(2), field + "[2]"));
    }

    private static Map<String, Object> enforceLimits(Map<String, Object> map) {
        boolean truncated = truncateStrings(map) || truncateLists(map) || truncateBytes(map);
        if (truncated) {
            map.put(KEY_TRUNCATED, true);
        }
        return map;
    }

    private static boolean truncateStrings(Map<String, Object> map) {
        boolean[] truncated = { false };
        walkStrings(map, value -> {
            if (value.length() > MAX_STRING_LENGTH) {
                truncated[0] = true;
                return value.substring(0, MAX_STRING_LENGTH);
            }
            return value;
        });
        return truncated[0];
    }

    @SuppressWarnings("unchecked")
    private static void walkStrings(Map<String, Object> node, UnaryOperator<String> transform) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                entry.setValue(transform.apply(text));
            } else if (value instanceof Map<?, ?>) {
                walkStrings((Map<String, Object>) value, transform);
            } else if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element instanceof Map<?, ?>) {
                        walkStrings((Map<String, Object>) element, transform);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean truncateLists(Map<String, Object> map) {
        boolean[] truncated = { false };
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof List<?> list && list.size() > MAX_LIST_ITEMS) {
                List<Object> kept = new ArrayList<>(list.subList(0, MAX_LIST_ITEMS));
                entry.setValue(kept);
                truncated[0] = true;
            }
        }
        return truncated[0];
    }

    @SuppressWarnings("unchecked")
    private static boolean truncateBytes(Map<String, Object> map) {
        if (jsonBytes(map) <= MAX_JSON_BYTES) {
            return false;
        }
        boolean truncated = false;
        String largestKey = null;
        List<Object> largest = null;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof List<?> list && !list.isEmpty()
                    && (largest == null || list.size() > largest.size())) {
                largestKey = entry.getKey();
                largest = (List<Object>) list;
            }
        }
        while (largest != null && jsonBytes(map) > MAX_JSON_BYTES && !largest.isEmpty()) {
            largest.remove(largest.size() - 1);
            truncated = true;
        }
        if (largest != null && largest.isEmpty() && jsonBytes(map) > MAX_JSON_BYTES) {
            map.remove(largestKey);
            truncated = true;
        }
        return truncated;
    }

    private static int jsonBytes(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsBytes(map).length;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Итог восприятия не сериализуется в JSON: " + e.getMessage(), e);
        }
    }
}
