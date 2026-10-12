package com.theminespirit.core.tactics.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DecisionSource;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalState;
import com.theminespirit.core.tactics.TacticsMemory;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class TacticsStateCodec {
    public static final int VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private TacticsStateCodec() {
    }

    public static String encode(StoredTactics stored) {
        Objects.requireNonNull(stored, "stored не может быть null: сохранять нечего");
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", VERSION);
        root.put("ordered", stored.ordered().name());
        stored.zone().ifPresent(zone -> {
            ObjectNode node = root.putObject("zone");
            putPoint(node.putObject("center"), zone.center());
            node.put("radius", zone.radius());
        });
        stored.ward().ifPresent(ward -> root.put("ward", ward));
        stored.memory().ifPresent(memory -> putMemory(root.putObject("memory"), memory));
        ObjectNode retreat = root.putObject("retreat");
        retreat.put("method", stored.retreat().method().name());
        retreat.put("since", stored.retreat().sinceTick());
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Состояние тактики не сериализовано в JSON: проверьте значения памяти", e);
        }
    }

    public static StoredTactics decode(String json) {
        Objects.requireNonNull(json, "json не может быть null: состояние тактики приходит текстом из ключа SpiritTactics");
        JsonNode root = readTree(json);
        requireObject(root, "состояние тактики");
        requireVersion(root);
        try {
            CombatMode ordered = enumField(CombatMode.class, root, "ordered");
            Optional<OrdersBuilder.Zone> zone = Optional.ofNullable(root.get("zone")).map(TacticsStateCodec::zone);
            Optional<String> ward = Optional.ofNullable(root.get("ward")).map(node -> text(node, "ward"));
            Optional<TacticsMemory> memory = Optional.ofNullable(root.get("memory")).map(TacticsStateCodec::memory);
            JsonNode retreatNode = required(root, "retreat");
            requireObject(retreatNode, "retreat");
            RetreatTracker retreat = new RetreatTracker(enumField(RetreatMethod.class, retreatNode, "method"),
                    longOf(required(retreatNode, "since"), "retreat.since"));
            return new StoredTactics(ordered, zone, ward, memory, retreat);
        } catch (IllegalArgumentException e) {
            throw new TacticsFormatException("Состояние тактики недопустимо: " + e.getMessage()
                    + ". Проверьте ключ SpiritTactics, файл не правьте вручную", e);
        }
    }

    private static void putMemory(ObjectNode node, TacticsMemory memory) {
        memory.previous().ifPresent(option -> node.put("previous", option.name()));
        node.put("previousSource", memory.previousSource().name());
        node.put("decidedAt", memory.decidedAt());
        node.put("state", memory.state().name());
        node.put("stateSince", memory.stateSince());
        node.put("mode", memory.mode().name());
        node.put("orderedMode", memory.orderedMode().name());
        memory.anchor().ifPresent(anchor -> putPoint(node.putObject("anchor"), anchor));
        node.put("lastSeenTick", memory.lastSeenTick());
        node.put("hpWindowRef", memory.hpWindowRef());
        node.put("hpWindowStart", memory.hpWindowStart());
    }

    private static TacticsMemory memory(JsonNode node) {
        requireObject(node, "memory");
        return new TacticsMemory(
                Optional.ofNullable(node.get("previous")).map(value -> enumValue(TacticalOption.class, value, "memory.previous")),
                enumField(DecisionSource.class, node, "previousSource"), longOf(required(node, "decidedAt"), "memory.decidedAt"),
                enumField(TacticalState.class, node, "state"), longOf(required(node, "stateSince"), "memory.stateSince"),
                enumField(CombatMode.class, node, "mode"), enumField(CombatMode.class, node, "orderedMode"),
                Optional.ofNullable(node.get("anchor")).map(TacticsStateCodec::point),
                longOf(required(node, "lastSeenTick"), "memory.lastSeenTick"),
                number(required(node, "hpWindowRef"), "memory.hpWindowRef"),
                longOf(required(node, "hpWindowStart"), "memory.hpWindowStart"));
    }

    private static OrdersBuilder.Zone zone(JsonNode node) {
        requireObject(node, "zone");
        return new OrdersBuilder.Zone(point(required(node, "center")), number(required(node, "radius"), "zone.radius"));
    }

    private static void putPoint(ObjectNode node, Point point) {
        node.put("x", point.x());
        node.put("y", point.y());
        node.put("z", point.z());
    }

    private static Point point(JsonNode node) {
        requireObject(node, "точка");
        return new Point(number(required(node, "x"), "x"), number(required(node, "y"), "y"),
                number(required(node, "z"), "z"));
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new TacticsFormatException("Состояние тактики не разобрано как JSON: проверьте значение ключа "
                    + "SpiritTactics, файл не правьте вручную", e);
        }
    }

    private static void requireVersion(JsonNode root) {
        JsonNode node = root.get("version");
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new TacticsFormatException("У состояния тактики нет целого поля version: без версии читатель "
                    + "не выберет разбор формата");
        }
        int version = node.intValue();
        if (version > VERSION) {
            throw new TacticsFormatException("Версия состояния тактики " + version + " новее поддерживаемой " + VERSION
                    + ": обновите мод или добавьте читателя новой версии");
        }
        if (version < 1) {
            throw new TacticsFormatException("Версия состояния тактики " + version + " меньше 1: версии начинаются с 1");
        }
    }

    private static void requireObject(JsonNode node, String what) {
        if (!node.isObject()) {
            throw new TacticsFormatException("«" + what + "» должно быть JSON-объектом, а получено " + node);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new TacticsFormatException("Нет обязательного поля '" + field + "' в состоянии тактики");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        if (!node.isTextual()) {
            throw new TacticsFormatException("Поле '" + field + "' должно быть строкой, а получено " + node);
        }
        return node.textValue();
    }

    private static double number(JsonNode node, String field) {
        if (!node.isNumber()) {
            throw new TacticsFormatException("Поле '" + field + "' должно быть числом, а получено " + node);
        }
        return node.doubleValue();
    }

    private static long longOf(JsonNode node, String field) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new TacticsFormatException("Поле '" + field + "' должно быть целым числом, а получено " + node);
        }
        return node.longValue();
    }

    private static <E extends Enum<E>> E enumField(Class<E> type, JsonNode parent, String field) {
        return enumValue(type, required(parent, field), field);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) {
        String name = text(node, field);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new TacticsFormatException("Поле '" + field + "' = '" + name + "': неизвестное значение "
                    + type.getSimpleName() + ", допустимы " + Arrays.toString(type.getEnumConstants()), e);
        }
    }
}
