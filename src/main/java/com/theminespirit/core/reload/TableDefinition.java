package com.theminespirit.core.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TableDefinition<T>(String id, List<String> resourcePaths, Parser<T> parser, Expander expander) {
    @FunctionalInterface
    public interface Parser<T> {
        T parse(List<LoadedContent> contents);
    }

    @FunctionalInterface
    public interface Expander {
        List<String> extraPaths(LoadedContent index);

        static Expander none() {
            return index -> List.of();
        }
    }

    public TableDefinition {
        Objects.requireNonNull(id, "id таблицы не может быть null: по нему ведётся список /spirit tables");
        Objects.requireNonNull(
                resourcePaths, "Пути ресурсов не могут быть null: по ним файлы ищутся в ресурс-менеджере");
        if (resourcePaths.isEmpty()) {
            throw new IllegalArgumentException(
                    "У таблицы '" + id + "' пустой список файлов: по пустому пути таблицу не найти");
        }
        resourcePaths = List.copyOf(resourcePaths);
        Objects.requireNonNull(parser, "Парсер не может быть null: таблицу нечем превратить в данные");
        Objects.requireNonNull(expander, "Расширение путей не может быть null: без него индекс не дочитает тела");
    }

    public static <T> TableDefinition<T> single(String id, String resourcePath, SingleParser<T> parser) {
        Objects.requireNonNull(parser, "Парсер не может быть null: таблицу нечем превратить в данные");
        return new TableDefinition<>(
                id,
                List.of(resourcePath),
                contents -> {
                    LoadedContent first = contents.get(0);
                    return parser.parse(first.json(), first.origin());
                },
                Expander.none());
    }

    public static <T> TableDefinition<T> composite(String id, List<String> resourcePaths, Parser<T> parser) {
        return new TableDefinition<>(id, resourcePaths, parser, Expander.none());
    }

    public static <T> TableDefinition<T> indexed(String id, String resourcePath, Expander expander, Parser<T> parser) {
        return new TableDefinition<>(id, List.of(resourcePath), parser, expander);
    }

    @FunctionalInterface
    public interface SingleParser<T> {
        T parse(String json, String origin);
    }

    Optional<List<LoadedContent>> read(ContentSource source) {
        List<LoadedContent> contents = new ArrayList<>(resourcePaths.size());
        for (String path : resourcePaths) {
            Optional<LoadedContent> loaded = source.read(path);
            if (loaded.isEmpty()) {
                return Optional.empty();
            }
            contents.add(loaded.get());
        }
        LoadedContent main = contents.get(0);
        for (String extra : expander.extraPaths(main)) {
            Optional<LoadedContent> loaded = source.read(extra);
            if (loaded.isEmpty()) {
                throw new IllegalStateException("Файл '" + extra + "', названный в '" + main.origin()
                        + "', не найден: тело таблицы без него не прочитать");
            }
            contents.add(loaded.get());
        }
        return Optional.of(List.copyOf(contents));
    }
}
