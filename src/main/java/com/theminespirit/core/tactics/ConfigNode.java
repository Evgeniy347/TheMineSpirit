package com.theminespirit.core.tactics;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class ConfigNode {
    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private final String file;
    private final String path;
    private final JsonNode node;

    private ConfigNode(String file, String path, JsonNode node) {
        this.file = file;
        this.path = path;
        this.node = node;
    }

    static ConfigNode root(String file, String json) {
        try {
            return new ConfigNode(file, "", MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(file + " → (корень): JSON не разобран (" + e.getOriginalMessage()
                    + "). Проверьте: синтаксис, кавычки, запятые, дубли ключей и кодировку UTF-8", e);
        }
    }

    IllegalStateException fail(String problem, String hint) {
        String where = path.isEmpty() ? "(корень)" : path;
        return new IllegalStateException(file + " → " + where + ": " + problem + ". Проверьте: " + hint);
    }

    String path() {
        return path;
    }

    String file() {
        return file;
    }

    ConfigNode fields(String[] required, String... optional) {
        if (!node.isObject()) {
            throw fail("ожидался объект, получено " + describe(), "в этом месте файла должен стоять объект { ... }");
        }
        Set<String> allowed = new TreeSet<>(Arrays.asList(required));
        allowed.addAll(Arrays.asList(optional));
        for (Iterator<String> names = node.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw child(name).fail("неизвестное поле '" + name + "'",
                        "допустимы только " + allowed + ", опечатка или поле из другой схемы");
            }
        }
        for (String name : required) {
            if (!node.has(name)) {
                throw child(name).fail("нет обязательного поля '" + name + "'", "добавьте поле или верните его из шаблона");
            }
        }
        return this;
    }

    boolean has(String name) {
        return node.has(name);
    }

    ConfigNode get(String name) {
        if (!node.has(name)) {
            throw child(name).fail("нет поля '" + name + "'", "добавьте поле");
        }
        return child(name);
    }

    Map<String, ConfigNode> entries() {
        if (!node.isObject()) {
            throw fail("ожидался объект, получено " + describe(), "в этом месте файла должен стоять объект { ... }");
        }
        Map<String, ConfigNode> result = new LinkedHashMap<>();
        for (Iterator<String> names = node.fieldNames(); names.hasNext();) {
            String name = names.next();
            result.put(name, child(name));
        }
        return result;
    }

    List<ConfigNode> items() {
        if (!node.isArray()) {
            throw fail("ожидался массив, получено " + describe(), "в этом месте файла должен стоять массив [ ... ]");
        }
        List<ConfigNode> result = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            result.add(new ConfigNode(file, path + "[" + i + "]", node.get(i)));
        }
        return result;
    }

    List<ConfigNode> nonEmptyItems() {
        List<ConfigNode> items = items();
        if (items.isEmpty()) {
            throw fail("пустая таблица", "таблица не может быть пустой: без строк решение не определено");
        }
        return items;
    }

    String text() {
        if (!node.isTextual()) {
            throw fail("ожидалась строка, получено " + describe(), "значение берётся в кавычки");
        }
        return node.asText();
    }

    boolean bool() {
        if (!node.isBoolean()) {
            throw fail("ожидалось true или false, получено " + describe(), "булево значение пишется без кавычек");
        }
        return node.asBoolean();
    }

    double number(double min, double max) {
        if (!node.isNumber()) {
            throw fail("ожидалось число, получено " + describe(), "число пишется без кавычек");
        }
        double value = node.asDouble();
        if (value < min || value > max) {
            throw fail("значение " + value + " вне диапазона [" + min + "; " + max + "]",
                    "допустимо от " + min + " до " + max);
        }
        return value;
    }

    int integer(int min, int max) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw fail("ожидалось целое число, получено " + describe(), "приоритеты и тики — целые числа без дробной части");
        }
        int value = node.asInt();
        if (value < min || value > max) {
            throw fail("значение " + value + " вне диапазона [" + min + "; " + max + "]",
                    "допустимо от " + min + " до " + max);
        }
        return value;
    }

    <E extends Enum<E>> E enumValue(Class<E> type) {
        String text = text();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text)) {
                return constant;
            }
        }
        throw fail("неизвестное значение '" + text + "' типа " + type.getSimpleName(),
                "допустимы " + Arrays.toString(type.getEnumConstants()) + ", опечатка в имени");
    }

    private ConfigNode child(String name) {
        String childPath = path.isEmpty() ? name : path + "." + name;
        return new ConfigNode(file, childPath, node.get(name));
    }

    private String describe() {
        return node == null ? "ничего" : node.getNodeType() + " " + node;
    }
}
