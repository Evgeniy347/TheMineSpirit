package com.theminespirit.core.intellect;

import com.theminespirit.core.processing.ProcessingStock;
import com.theminespirit.core.script.CatalogLimits;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Итог {@code acquire} для намерения интеллекта: намерение говорит, сколько добавить ({@code more}), а {@code acquire}
 * считает цель по инвентарю духа — без поправки на свой запас дух с 8 досками получал бы «добудь 8 досок», сразу
 * «успевал» и запас поселения не рос. Доски — из своей самой обильной породы брёвен: иначе дух с брёвнами джунглей
 * рубил бы дуб под «дубовые доски» поселения.
 */
public record AcquireOrder(String item, int count) {
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final String PLANKS_SUFFIX = "_planks";

    public AcquireOrder {
        Objects.requireNonNull(item, "Предмет acquire не может быть null: без него непонятно, что добыть");
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Цель acquire " + count + " вне [" + CatalogLimits.COUNT_MIN + ", "
                    + CatalogLimits.COUNT_MAX + "]: скрипт её не примет");
        }
    }

    public static AcquireOrder of(String item, int more, Map<String, Integer> held) {
        Objects.requireNonNull(item, "Предмет намерения не может быть null: без него непонятно, что добыть");
        Objects.requireNonNull(held, "Инвентарь духа не может быть null: пустой задают пустой картой");
        if (more < 1) {
            throw new IllegalArgumentException("Добавить " + more + " предметов: намерение без прироста не исполняют");
        }
        String target = shortId(item);
        if (target.endsWith(PLANKS_SUFFIX)) {
            target = ownPlanks(held).orElse(target);
        }
        long total = (long) heldOf(held, target) + more;
        return new AcquireOrder(target, (int) Math.min(total, CatalogLimits.COUNT_MAX));
    }

    private static Optional<String> ownPlanks(Map<String, Integer> held) {
        return held.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .filter(entry ->
                        ProcessingStock.planksOf(shortId(entry.getKey())).isPresent())
                .filter(entry -> !shortId(entry.getKey()).endsWith(PLANKS_SUFFIX))
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> shortId(entry.getKey()), Comparator.reverseOrder()))
                .flatMap(entry -> ProcessingStock.planksOf(shortId(entry.getKey())));
    }

    private static int heldOf(Map<String, Integer> held, String item) {
        int count = 0;
        for (Map.Entry<String, Integer> entry : held.entrySet()) {
            if (entry.getValue() != null && shortId(entry.getKey()).equals(item)) {
                count += entry.getValue();
            }
        }
        return count;
    }

    private static String shortId(String id) {
        return id.startsWith(MINECRAFT_PREFIX) ? id.substring(MINECRAFT_PREFIX.length()) : id;
    }
}
