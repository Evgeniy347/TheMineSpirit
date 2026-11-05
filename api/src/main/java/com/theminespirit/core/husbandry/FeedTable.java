package com.theminespirit.core.husbandry;

import static com.theminespirit.core.Lookup.present;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public record FeedTable(Map<Species, List<String>> feeds) {
    public static final String RESOURCE = "data/theminespirit/husbandry/feed.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile Supplier<FeedTable> source = FeedTable::defaults;
    private static final FeedTable DEFAULTS = load(Map.of(
            "cow", List.of("minecraft:wheat"),
            "sheep", List.of("minecraft:wheat"),
            "pig", List.of("minecraft:carrot"),
            "chicken", List.of("minecraft:wheat_seeds")));

    public FeedTable {
        Objects.requireNonNull(feeds, "Таблица кормов не может быть null: без неё неизвестно, чем кормить стадо");
        Map<Species, List<String>> checked = new EnumMap<>(Species.class);
        for (Species species : Species.all()) {
            List<String> foods = feeds.get(species);
            if (foods == null || foods.isEmpty()) {
                throw new IllegalStateException("У вида " + species + " нет кормов: вид без корма нельзя ни кормить, "
                        + "ни разводить, дополните таблицу");
            }
            checked.put(species, List.copyOf(foods));
        }
        feeds = Map.copyOf(checked);
    }

    public static List<String> feedFor(Species species) {
        Objects.requireNonNull(species, "Вид животного не может быть null: без него неизвестно, чем кормить стадо");
        return source.get().feedForInstance(species);
    }

    public static void useSource(Supplier<FeedTable> supplier) {
        source = Objects.requireNonNull(
                supplier, "Источник таблицы кормов не может быть null: без него загрузка таблицы не видна");
    }

    public static FeedTable defaults() {
        return DEFAULTS;
    }

    public static FeedTable load(Map<String, List<String>> raw) {
        Objects.requireNonNull(raw, "Данные таблицы кормов не могут быть null: разбирать нечего");
        Map<Species, List<String>> feeds = new EnumMap<>(Species.class);
        for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
            Species species = Species.parse(entry.getKey());
            List<String> foods = entry.getValue();
            if (foods == null || foods.isEmpty()) {
                throw new IllegalStateException("У вида '" + entry.getKey()
                        + "' пустой список кормов: вид без корма пропускать молча нельзя, исправьте файл " + RESOURCE);
            }
            List<String> canonical = new ArrayList<>(foods.size());
            for (String food : foods) {
                String id = canonicalId(food);
                if (canonical.contains(id)) {
                    throw new IllegalStateException("У вида '" + entry.getKey() + "' корм '" + id
                            + "' повторяется: дубль в списке ничего не значит, уберите повтор в файле " + RESOURCE);
                }
                canonical.add(id);
            }
            if (feeds.put(species, List.copyOf(canonical)) != null) {
                throw new IllegalStateException("Вид '" + entry.getKey() + "' задан дважды: второй список молча "
                        + "затёр бы первый, исправьте файл " + RESOURCE);
            }
        }
        return new FeedTable(feeds);
    }

    public static FeedTable loadJson(String json, String origin) {
        Objects.requireNonNull(json, "Текст таблицы кормов не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение об ошибке называет путь");
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Таблица '" + origin + "' — не разобран JSON: " + error.getOriginalMessage()
                    + ". Исправьте синтаксис файла");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Таблица '" + origin + "' — не объект: на верхнем уровне ожидается "
                    + "объект «вид → список кормов»");
        }
        Map<String, List<String>> raw = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            raw.put(field.getKey(), JsonResource.strings(field.getValue(), origin, "вида '" + field.getKey() + "'"));
        }
        return load(raw);
    }

    public static FeedTable loadFile() {
        return loadJson(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public List<String> feedForInstance(Species species) {
        Objects.requireNonNull(species, "Вид животного не может быть null: без него неизвестно, чем кормить стадо");
        return present(feeds(), species, "Таблица кормов");
    }

    private static String canonicalId(String food) {
        Objects.requireNonNull(food, "id корма не может быть null: по нему ищется предмет в инвентаре");
        return food.indexOf(':') < 0 ? "minecraft:" + food : food;
    }
}
