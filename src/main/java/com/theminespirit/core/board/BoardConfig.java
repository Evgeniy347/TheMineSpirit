package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Живые числа доски: приоритеты кейсов, модификаторы приоритета и интервалы. Читается из
 * JSON-таблицы через {@code DataTables} — правится датапаком мира и перезагружается
 * по {@code /reload} без перезапуска. Ключи приоритетов — имена {@link BoardCase},
 * ключи модификаторов — {@link BoardPriorities#MODIFIER_KEYS},
 * ключи интервалов — {@link BoardTuning#INTERVAL_KEYS}. Пустые карты допустимы: недостающее
 * добирают значения по умолчанию из {@code BoardRules}, {@code BoardPriorities} и {@code BoardTuning}.
 */
public record BoardConfig(Map<String, Integer> priorities, Map<String, Integer> modifiers,
        Map<String, Long> intervals, Map<String, Integer> levels, Map<String, Integer> herd,
        List<String> immovable, Map<String, Integer> supplies,
        Map<String, Map<String, Integer>> products) {
    public static final String RESOURCE = "data/theminespirit/board/board.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public BoardConfig {
        Objects.requireNonNull(priorities,
                "Приоритеты не могут быть null: пустую таблицу задают пустой картой, а не null");
        priorities = Map.copyOf(priorities);
        Objects.requireNonNull(modifiers,
                "Модификаторы не могут быть null: пустую таблицу задают пустой картой, а не null");
        modifiers = Map.copyOf(modifiers);
        Objects.requireNonNull(intervals,
                "Интервалы не могут быть null: пустую таблицу задают пустой картой, а не null");
        intervals = Map.copyOf(intervals);
        Objects.requireNonNull(levels,
                "Уровни не могут быть null: пустую таблицу задают пустой картой, а не null");
        levels = Map.copyOf(levels);
        Objects.requireNonNull(herd,
                "Данные стада не могут быть null: пустую таблицу задают пустой картой, а не null");
        herd = Map.copyOf(herd);
        Objects.requireNonNull(immovable,
                "Неподвижные постройки не могут быть null: пустой список задают пустым списком, а не null");
        immovable = List.copyOf(immovable);
        Objects.requireNonNull(supplies,
                "Запасы не могут быть null: пустую таблицу задают пустой картой, а не null");
        supplies = Map.copyOf(supplies);
        Objects.requireNonNull(products,
                "Продукты не могут быть null: пустую таблицу задают пустой картой, а не null");
        Map<String, Map<String, Integer>> checkedProducts = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : products.entrySet()) {
            checkedProducts.put(entry.getKey(),
                    Map.copyOf(Objects.requireNonNull(entry.getValue(),
                            "Продукты вида '" + entry.getKey() + "' не могут быть null: пустую "
                                    + "таблицу задают пустой картой")));
        }
        products = Map.copyOf(checkedProducts);
    }

    public BoardConfig(Map<String, Integer> priorities, Map<String, Integer> modifiers,
            Map<String, Long> intervals) {
        this(priorities, modifiers, intervals, Map.of(), Map.of(), List.of(), Map.of(), Map.of());
    }

    public static BoardConfig fromJson(String json, String origin) {
        Objects.requireNonNull(json, "Текст таблицы не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение об ошибке называет путь");
        JsonNode root = JsonResource.parse(json, origin);
        if (!root.isObject()) {
            throw new IllegalStateException("Таблица '" + origin + "' — не объект: на верхнем уровне ожидается "
                    + "объект «priorities → modifiers → intervals»");
        }
        Map<String, Integer> priorities = integers(root.get("priorities"), origin, "priorities",
                BoardCase::valueOf, false);
        Map<String, Integer> modifiers = integers(root.get("modifiers"), origin, "modifiers",
                BoardPriorities::modifierKey, true);
        Map<String, Long> intervals = longs(root.get("intervals"), origin, "intervals", BoardTuning::intervalKey);
        Map<String, Integer> levels = integers(root.get("levels"), origin, "levels", BoardCase::valueOf, false);
        Map<String, Integer> supplies = integers(root.get("supplies"), origin, "supplies",
                BoardConfig::supplyKey, false);
        Map<String, Integer> herd = integers(root.get("herd"), origin, "herd", HerdCap::herdKey, false);
        List<String> immovable = caseList(root.get("immovable"), origin, "immovable");
        Map<String, Map<String, Integer>> products = productMap(root.get("products"), origin, "products");
        return new BoardConfig(priorities, modifiers, intervals, levels, herd, immovable, supplies, products);
    }

    public static BoardConfig readFromClasspath() {
        return fromJson(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    private static Map<String, Integer> integers(JsonNode node, String origin, String field,
            Function<String, ?> known, boolean allowNegative) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "' — не объект: ожидается "
                    + "карта «имя → число»");
        }
        Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String key = entry.getKey();
            checkKey(known, key, origin, field);
            JsonNode value = entry.getValue();
            if (!value.isIntegralNumber()) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key
                        + "' не целое число, получено " + value.getNodeType()
                        + ": приоритет сравнивается числом, строка сломает сортировку выдачи");
            }
            int number = value.intValue();
            if (!allowNegative && number < 0) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key + "' равен " + number
                        + " и отрицателен: приоритет вне [0, " + BoardTuning.MAX_PRIORITY
                        + "] не участвует в сравнении выдачи");
            }
            values.put(key, number);
        }
        return values;
    }

    private static Map<String, Long> longs(JsonNode node, String origin, String field,
            Function<String, ?> known) {
        Map<String, Long> values = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "' — не объект: ожидается "
                    + "карта «имя → число»");
        }
        Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String key = entry.getKey();
            checkKey(known, key, origin, field);
            JsonNode value = entry.getValue();
            if (!value.isIntegralNumber()) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key
                        + "' не целое число, получено " + value.getNodeType()
                        + ": интервал считается в тиках, строка сломает пересборку доски");
            }
            long number = value.longValue();
            if (number < 0) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key + "' равен " + number
                        + " и отрицателен: интервал и TTL не бывают отрицательными");
            }
            values.put(key, number);
        }
        return values;
    }

    private static List<String> caseList(JsonNode node, String origin, String field) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (!node.isArray()) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "' — не массив: ожидается "
                    + "список имён кейсов");
        }
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': элемент обязан "
                        + "быть непустой строкой-именем кейса, получено: " + item);
            }
            String key = item.textValue();
            try {
                BoardCase.valueOf(key);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': кейс '" + key
                        + "' неизвестен: опечатка в имени молча потеряла бы строку таблицы", unknown);
            }
            values.add(key);
        }
        return List.copyOf(values);
    }

    private static Map<String, Map<String, Integer>> productMap(JsonNode node, String origin, String field) {
        Map<String, Map<String, Integer>> values = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "' — не объект: ожидается "
                    + "карта «вид → предмет → норма на душу»");
        }
        Iterator<Map.Entry<String, JsonNode>> species = node.fields();
        while (species.hasNext()) {
            Map.Entry<String, JsonNode> entry = species.next();
            String speciesName = entry.getKey();
            try {
                Species.parse(speciesName);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': вид '" + speciesName
                        + "' неизвестен: опечатка в имени молча потеряла бы норму продуктов", unknown);
            }
            JsonNode items = entry.getValue();
            if (!items.isObject()) {
                throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': у вида '" + speciesName
                        + "' ожидается объект «предмет → норма на душу»");
            }
            Map<String, Integer> perCapita = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> productFields = items.fields();
            while (productFields.hasNext()) {
                Map.Entry<String, JsonNode> product = productFields.next();
                if (product.getKey().isBlank()) {
                    throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': у вида '"
                            + speciesName + "' пустое имя предмета");
                }
                JsonNode value = product.getValue();
                if (!value.isIntegralNumber() || value.intValue() < 0) {
                    throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': у вида '"
                            + speciesName + "' норма '" + product.getKey() + "' обязана быть "
                            + "неотрицательным целым, получено " + value);
                }
                perCapita.put(product.getKey(), value.intValue());
            }
            values.put(speciesName, Map.copyOf(perCapita));
        }
        return Map.copyOf(values);
    }

    private static String supplyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("пустое имя предмета");
        }
        return key;
    }

    private static void checkKey(Function<String, ?> known, String key, String origin,
            String field) {
        try {
            known.apply(key);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': ключ '" + key
                    + "' неизвестен: опечатка в имени молча потеряла бы строку таблицы", unknown);
        }
    }
}
