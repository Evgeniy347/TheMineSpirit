package com.theminespirit.core.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class JsonResource {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonResource() {
    }

    public static String readClasspath(String resourcePath) {
        Objects.requireNonNull(resourcePath,
                "Путь к файлу данных не может быть null: по нему файл ищется на classpath");
        try (InputStream stream = JsonResource.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Файл '" + resourcePath
                        + "' не найден на classpath: проверьте, что он лежит в src/main/resources/"
                        + resourcePath.substring(0, resourcePath.lastIndexOf('/')));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Файл '" + resourcePath
                    + "' не прочитан: проверьте права и кодировку файла", error);
        }
    }

    public static JsonNode parse(String json, String origin) {
        Objects.requireNonNull(json, "Текст файла не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник файла не может быть null: сообщение об ошибке называет путь");
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Файл '" + origin + "' — не разобран JSON: "
                    + error.getOriginalMessage() + ". Исправьте синтаксис файла");
        }
        if (root == null) {
            throw new IllegalStateException("Файл '" + origin + "' пуст: в нём нет ни одного правила");
        }
        if (!root.isObject()) {
            throw new IllegalStateException("Файл '" + origin + "' — не объект: "
                    + "на верхнем уровне ожидается JSON-объект с полями");
        }
        return root;
    }

    public static List<JsonNode> array(JsonNode node, String origin, String what) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin + "': у " + what
                    + " ожидается непустой список: без него правило не описывает ничего");
        }
        List<JsonNode> items = new ArrayList<>(node.size());
        for (int index = 0; index < node.size(); index++) {
            items.add(node.get(index));
        }
        return items;
    }

    public static List<String> strings(JsonNode node, String origin, String what) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array(node, origin, what)) {
            if (!item.isTextual()) {
                throw new IllegalStateException("Файл '" + origin + "': у " + what
                        + " элемент не строка: шаблоны и имена задаются текстом");
            }
            values.add(item.asText());
        }
        return values;
    }

    public static List<String> optionalStrings(JsonNode node, String origin, String what) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        return strings(node, origin, what);
    }

    public static String text(JsonNode node, String origin, String what) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalStateException("Файл '" + origin + "': у " + what
                    + " ожидается непустая строка: значение задаётся текстом");
        }
        return node.asText();
    }

    public static int integer(JsonNode node, String origin, String what) {
        if (node == null || !node.isInt()) {
            throw new IllegalStateException("Файл '" + origin + "': у " + what
                    + " ожидается целое число: дробное или текстовое значение правила не имеет смысла");
        }
        return node.asInt();
    }

    public static void checkFields(JsonNode node, Set<String> allowed, String origin, String what) {
        Objects.requireNonNull(node, "Узел файла не может быть null: поля проверяются у объекта");
        List<String> fields = new ArrayList<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            fields.add(names.next());
        }
        for (String field : fields) {
            if (!allowed.contains(field)) {
                throw new IllegalStateException("Файл '" + origin + "': неизвестное поле '" + field + "' у "
                        + what + ": допустимы " + allowed + ", опечатка в имени поля сделала бы правило мёртвым");
            }
        }
    }
}
