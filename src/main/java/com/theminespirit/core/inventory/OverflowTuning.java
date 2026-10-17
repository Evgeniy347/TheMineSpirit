package com.theminespirit.core.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.Set;

public record OverflowTuning(String origin, int stockScale, double minFactor) {
    public static final String RESOURCE = "data/theminespirit/inventory/overflow.json";

    private static final Set<String> FIELDS = Set.of("stock_scale", "min_factor");
    private static final int DEFAULT_STOCK_SCALE = 64;
    private static final double DEFAULT_MIN_FACTOR = 0.25;

    public OverflowTuning {
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("Источник таблицы переполнения пуст: сообщение называет файл");
        }
        if (stockScale < 1) {
            throw new IllegalArgumentException("Масштаб запаса " + stockScale
                    + " меньше 1: на нуле ценность не падала бы никогда");
        }
        if (Double.isNaN(minFactor) || Double.isInfinite(minFactor) || minFactor <= 0 || minFactor > 1) {
            throw new IllegalArgumentException("Нижний коэффициент " + minFactor
                    + " вне (0, 1]: нулевой или отрицательный обнулил бы ценность любого склада");
        }
    }

    public static OverflowTuning defaults() {
        return new OverflowTuning("defaults", DEFAULT_STOCK_SCALE, DEFAULT_MIN_FACTOR);
    }

    public static OverflowTuning load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static OverflowTuning parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "файла переполнения инвентаря");
        int stockScale = JsonResource.integer(root.get("stock_scale"), origin, "поля stock_scale");
        double minFactor = JsonResource.decimal(root.get("min_factor"), origin, "поля min_factor");
        try {
            return new OverflowTuning(origin, stockScale, minFactor);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public double stockFactor(int stock) {
        if (stock <= 0) {
            return 1.0;
        }
        double factor = 1.0 / (1.0 + (double) stock / stockScale);
        return Math.max(minFactor, factor);
    }

    public int effectiveUnitValue(int baseValue, int stock) {
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: по нему видно, откуда коэффициент");
        if (baseValue <= 0) {
            return 0;
        }
        return (int) Math.floor(baseValue * stockFactor(stock));
    }

    public int effectiveStackValue(ValueTable values, Stack stack, int stock) {
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: по ней считается базовая цена");
        Objects.requireNonNull(stack, "Стопка не может быть null: ценность считается по её количеству");
        return effectiveUnitValue(values.valueOf(stack.item()), stock) * stack.count();
    }
}
