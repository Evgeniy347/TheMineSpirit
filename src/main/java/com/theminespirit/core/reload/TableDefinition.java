package com.theminespirit.core.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Описание таблицы для перезагрузки: id (для /spirit tables), путь(и) ресурсов (data/theminespirit/...) и парсер
 * «json → T». Парсер — чистая функция ядра, поэтому и загрузка, и её ошибки тестируются без игры.
 *
 * <p>У таблицы один файл ({@link #single}) или несколько ({@link #composite}, как четыре файла тактики): для
 * составной таблицы парсер получает содержимое всех файлов, а сама таблица в реестре — одна строка.
 */
public record TableDefinition<T>(String id, List<String> resourcePaths, Parser<T> parser) {
    public interface Parser<T> {
        T parse(List<LoadedContent> contents);
    }

    public TableDefinition {
        Objects.requireNonNull(id, "id таблицы не может быть null: по нему ведётся список /spirit tables");
        Objects.requireNonNull(resourcePaths, "Пути ресурсов не могут быть null: по ним файлы ищутся в ресурс-менеджере");
        if (resourcePaths.isEmpty()) {
            throw new IllegalArgumentException("У таблицы '" + id
                    + "' пустой список файлов: по пустому пути таблицу не найти");
        }
        resourcePaths = List.copyOf(resourcePaths);
        Objects.requireNonNull(parser, "Парсер не может быть null: таблицу нечем превратить в данные");
    }

    public static <T> TableDefinition<T> single(String id, String resourcePath, SingleParser<T> parser) {
        Objects.requireNonNull(parser, "Парсер не может быть null: таблицу нечем превратить в данные");
        return new TableDefinition<>(id, List.of(resourcePath), contents -> {
            LoadedContent first = contents.get(0);
            return parser.parse(first.json(), first.origin());
        });
    }

    public static <T> TableDefinition<T> composite(String id, List<String> resourcePaths, Parser<T> parser) {
        return new TableDefinition<>(id, resourcePaths, parser);
    }

    public interface SingleParser<T> {
        T parse(String json, String origin);
    }

    /**
     * Читает все файлы таблицы из источника. Пустой Optional у файла — файла нет: это не ошибка переопределения,
     * поэтому такой файл просто не попадает в список (парсер сам решает, что делать с неполным набором).
     */
    Optional<List<LoadedContent>> read(ContentSource source) {
        List<LoadedContent> contents = new ArrayList<>(resourcePaths.size());
        for (String path : resourcePaths) {
            Optional<LoadedContent> loaded = source.read(path);
            if (loaded.isEmpty()) {
                return Optional.empty();
            }
            contents.add(loaded.get());
        }
        return Optional.of(List.copyOf(contents));
    }
}