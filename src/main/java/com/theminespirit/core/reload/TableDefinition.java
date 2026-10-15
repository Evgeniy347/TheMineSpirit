package com.theminespirit.core.reload;

import java.util.Objects;

/**
 * Описание таблицы для перезагрузки: id (для /spirit tables), путь ресурса (data/theminespirit/...) и парсер
 * «json → T». Парсер — чистая функция ядра, поэтому и загрузка, и её ошибки тестируются без игры.
 */
public record TableDefinition<T>(String id, String resourcePath, Parser<T> parser) {
    public interface Parser<T> {
        T parse(String json, String origin);
    }

    public TableDefinition {
        Objects.requireNonNull(id, "id таблицы не может быть null: по нему ведётся список /spirit tables");
        Objects.requireNonNull(resourcePath, "Путь ресурса не может быть null: по нему файл ищется в ресурс-менеджере");
        Objects.requireNonNull(parser, "Парсер не может быть null: таблицу нечем превратить в данные");
    }
}