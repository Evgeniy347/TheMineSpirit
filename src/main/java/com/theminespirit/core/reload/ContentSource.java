package com.theminespirit.core.reload;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Источник содержимого таблиц на момент перезагрузки. Ядро не знает про ресурс-менеджер NeoForge: адаптер
 * передаёт реализацию, читающую json из серверных ресурсов (мод и датапаки мира). Для тестов — карта.
 */
public interface ContentSource {
    /**
     * Читает содержимое по пути вида {@code data/theminespirit/tactics/weights.json}. Пустой Optional — файла нет
     * (тогда остаётся текущая версия без ошибки: отсутствие переопределения — не сбой). Ошибка чтения бросается
     * исключением и попадает в статус таблицы.
     */
    Optional<LoadedContent> read(String resourcePath);

    static ContentSource of(Map<String, LoadedContent> content) {
        Objects.requireNonNull(content, "Карта содержимого не может быть null: тестовый источник без неё пуст");
        Map<String, LoadedContent> copy = Map.copyOf(content);
        return resourcePath -> Optional.ofNullable(copy.get(resourcePath));
    }
}