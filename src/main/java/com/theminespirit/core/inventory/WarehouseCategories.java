package com.theminespirit.core.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class WarehouseCategories {
    public static final String RESOURCE = "data/theminespirit/inventory/warehouse_categories.json";
    public static final int VERSION = 1;

    private static final Set<String> ROOT_FIELDS = Set.of("version", "categories");
    private static final Set<String> CATEGORY_FIELDS = Set.of("name", "match");

    private final List<Category> categories;
    private final Map<String, Category> byName;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private WarehouseCategories(List<Category> categories) {
        this.categories = List.copyOf(categories);
        Map<String, Category> index = new LinkedHashMap<>();
        for (Category category : this.categories) {
            if (index.put(category.name(), category) != null) {
                throw new IllegalStateException("Категория склада '" + category.name()
                        + "' объявлена дважды: повтор делает раскладку и табличку неопределёнными");
            }
        }
        this.byName = Map.copyOf(index);
    }

    public static WarehouseCategories load() {
        return load(RESOURCE);
    }

    public static WarehouseCategories load(String resourcePath) {
        return parse(JsonResource.readClasspath(resourcePath), resourcePath);
    }

    public static WarehouseCategories parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "файла категорий склада");
        int version = JsonResource.integer(root.get("version"), origin, "поля version категорий склада");
        if (version != VERSION) {
            throw new IllegalStateException("Файл '" + origin + "': версия категорий " + version + ", ожидается "
                    + VERSION + ": категории другой версии читаются иначе");
        }
        JsonNode node = root.get("categories");
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin + "' без списка categories: без категорий "
                    + "склад не разложит предметы и не подпишет таблички");
        }
        List<Category> categories = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode item : JsonResource.items(node)) {
            if (!item.isObject()) {
                throw new IllegalStateException(
                        "Файл '" + origin + "': категория склада обязана быть объектом с полями name и match");
            }
            JsonResource.checkFields(item, CATEGORY_FIELDS, origin, "категории склада");
            String name = JsonResource.text(item.get("name"), origin, "поля name категории склада");
            Category category;
            try {
                category = new Category(
                        name, JsonResource.strings(item.get("match"), origin, "поля match категории '" + name + "'"));
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException(
                        "Файл '" + origin + "': категория '" + name + "' неверна — " + error.getMessage(), error);
            }
            names.add(name);
            categories.add(category);
        }
        if (names.size() != categories.size()) {
            throw new IllegalStateException("Файл '" + origin + "': имена категорий склада повторяются: "
                    + "порядок задаёт раскладку и подписи, повтор делает их неопределёнными");
        }
        Category last = categories.get(categories.size() - 1);
        if (!last.match().contains("*")) {
            throw new IllegalStateException("Файл '" + origin + "': последняя категория '" + last.name()
                    + "' не содержит шаблон '*': предмет вне категорий остался бы без сундука и таблички");
        }
        return new WarehouseCategories(categories);
    }

    public String categoryOf(String item) {
        Objects.requireNonNull(item, "Предмет не назван: категория склада определяется по id");
        return cache.computeIfAbsent(item, this::match);
    }

    private String match(String item) {
        for (Category category : categories) {
            if (category.covers(item)) {
                return category.name();
            }
        }
        throw new IllegalStateException("Предмет '" + item + "' не попал ни в одну категорию склада: "
                + "последняя категория обязана содержать '*', проверьте файл категорий");
    }

    public List<String> names() {
        return categories.stream().map(Category::name).toList();
    }

    public String categoryOfSlots(List<Stack> slots) {
        Objects.requireNonNull(slots, "Слоты контейнера не могут быть null: категория берётся из содержимого");
        for (Stack stack : slots) {
            if (stack != null) {
                return categoryOf(stack.item());
            }
        }
        return "";
    }

    public boolean known(@Nullable String name) {
        return name != null && byName.containsKey(name);
    }

    public int rank(String name) {
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).name().equals(name)) {
                return index;
            }
        }
        return categories.size();
    }

    public List<Category> categories() {
        return categories;
    }
}
