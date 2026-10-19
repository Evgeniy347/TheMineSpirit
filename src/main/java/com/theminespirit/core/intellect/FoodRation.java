package com.theminespirit.core.intellect;

import com.theminespirit.core.processing.CookableFoods;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Решение о раздаче еды духу с личной едой ниже нормы: что взять со склада и не сырьё ли это.
 * Сытная (готовая) еда приоритетнее сырья; если готовой нет, а станция готовки есть, сырьё
 * сначала идёт на готовку и раздаче не подлежит. Мир из ядра не читается: запас и наличие
 * станции приносит адаптер.
 */
public final class FoodRation {
    private static final String DEFAULT_NAMESPACE = "minecraft:";

    public record Choice(String item, boolean needsCooking) {
        public Choice {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException(
                        "Еда для раздачи без имени: со склада берут конкретный предмет, пустое имя не выбрать");
            }
        }
    }

    private FoodRation() {}

    public static boolean due(double personalNutrition, double norm) {
        if (Double.isNaN(personalNutrition) || Double.isInfinite(personalNutrition) || personalNutrition < 0) {
            throw new IllegalArgumentException("Личная еда = " + personalNutrition
                    + ": сытность инвентаря обязана быть конечной и >= 0, отрицательного запаса не бывает");
        }
        if (Double.isNaN(norm) || Double.isInfinite(norm) || norm <= 0) {
            throw new IllegalArgumentException("Норма еды на душу = " + norm
                    + ": суточная норма обязана быть конечной и > 0, иначе дефицит не определён");
        }
        return personalNutrition < norm;
    }

    public static Optional<Choice> choose(Map<String, Integer> stock, NutritionTable table, boolean cookingStation) {
        Objects.requireNonNull(stock, "Раздача еды без описи склада не выбирается: пустой набор задают пустой картой");
        Objects.requireNonNull(table, "Раздача еды без таблицы сытности не выбирается: сытность — данные");
        Optional<String> ready = bestReady(stock, table);
        if (ready.isPresent()) {
            return Optional.of(new Choice(ready.get(), false));
        }
        Optional<String> raw = bestRaw(stock, table);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Choice(raw.get(), cookingStation));
    }

    private static Optional<String> bestReady(Map<String, Integer> stock, NutritionTable table) {
        return best(
                stock,
                item -> table.nutrition().containsKey(item) && !CookableFoods.isRaw(item),
                item -> table.nutrition().getOrDefault(item, 0.0));
    }

    private static Optional<String> bestRaw(Map<String, Integer> stock, NutritionTable table) {
        return best(stock, CookableFoods::isRaw, item -> {
            Double value =
                    CookableFoods.cooked(item).map(table.nutrition()::get).orElse(null);
            return value == null ? 0.0 : value;
        });
    }

    private static Optional<String> best(
            Map<String, Integer> stock, Predicate<String> kind, ToDoubleFunction<String> nutrition) {
        return stock.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> qualify(entry.getKey()))
                .filter(kind)
                .max(Comparator.<String>comparingDouble(nutrition).thenComparing(Comparator.reverseOrder()));
    }

    private static String qualify(String item) {
        return item.indexOf(':') < 0 ? DEFAULT_NAMESPACE + item : item;
    }
}
