package com.theminespirit.core.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class StockListing {
    private static final String DEFAULT_NAMESPACE = "minecraft";

    public enum Sort {
        COUNT,
        NAME,
        MOD;

        public Sort next() {
            return switch (this) {
                case COUNT -> NAME;
                case NAME -> MOD;
                case MOD -> COUNT;
            };
        }
    }

    public record Entry(int key, String id, String name, int count) {
        public Entry {
            Objects.requireNonNull(id, "Id предмета списка не может быть null");
            Objects.requireNonNull(name, "Имя предмета списка не может быть null: пустое — пустая строка");
            if (count < 0) {
                throw new IllegalArgumentException("Количество '" + id + "' отрицательное: " + count);
            }
        }

        public String namespace() {
            int colon = id.indexOf(':');
            return colon < 0 ? DEFAULT_NAMESPACE : id.substring(0, colon);
        }
    }

    private StockListing() {}

    public static List<Entry> view(List<Entry> all, String query, Sort sort, boolean reversed) {
        Objects.requireNonNull(all, "Список вещей не может быть null");
        Objects.requireNonNull(query, "Строка поиска не может быть null: пустая — без фильтра");
        Objects.requireNonNull(sort, "Порядок сортировки не может быть null");
        List<String> tokens = List.of(query.toLowerCase(Locale.ROOT).trim().split("\\s+"));
        Comparator<Entry> order = comparator(sort);
        return all.stream()
                .filter(entry -> matches(entry, tokens))
                .sorted(reversed ? order.reversed() : order)
                .toList();
    }

    private static boolean matches(Entry entry, List<String> tokens) {
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("@")) {
                if (!entry.namespace().startsWith(token.substring(1))) {
                    return false;
                }
            } else if (!entry.name().toLowerCase(Locale.ROOT).contains(token)
                    && !entry.id().toLowerCase(Locale.ROOT).contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static Comparator<Entry> comparator(Sort sort) {
        Comparator<Entry> byName = Comparator.comparing(
                        (Entry entry) -> entry.name().toLowerCase(Locale.ROOT))
                .thenComparing(Entry::id)
                .thenComparingInt(Entry::key);
        return switch (sort) {
            case COUNT -> Comparator.comparingInt(Entry::count).reversed().thenComparing(byName);
            case NAME -> byName;
            case MOD -> Comparator.comparing(Entry::namespace).thenComparing(byName);
        };
    }
}
