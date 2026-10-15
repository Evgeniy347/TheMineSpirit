package com.theminespirit.core.reload;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Единственный реестр держателей таблиц: одно место регистрации (адаптер добавляет определения один раз),
 * одна точка перезагрузки ({@link #reload}). Каждая таблица грузится независимо: ошибка в одной не мешает
 * остальным и не подменяет её содержимое пустым.
 */
public final class TableRegistry {
    private final Map<String, Registered<?>> byId = new LinkedHashMap<>();
    private final Clock clock;

    public TableRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Часы не могут быть null: по ним штампуется время загрузки");
    }

    public <T> TableHolder<T> register(TableDefinition<T> definition, T initial) {
        Objects.requireNonNull(definition, "Определение таблицы не может быть null: регистрировать нечего");
        if (byId.containsKey(definition.id())) {
            throw new IllegalStateException("Таблица с id '" + definition.id()
                    + "' уже зарегистрирована: два держателя на одну таблицу разошлись бы версиями");
        }
        TableHolder<T> holder = new TableHolder<>(definition.id(), initial, TableStatus.Source.BUILTIN, "classpath",
                TableHolder.hashOf(String.valueOf(initial)), clock);
        byId.put(definition.id(), new Registered<>(definition, holder));
        return holder;
    }

    public Optional<TableHolder<?>> holder(String id) {
        Registered<?> found = byId.get(id);
        return found == null ? Optional.empty() : Optional.of(found.holder());
    }

    public List<TableHolder<?>> holders() {
        List<TableHolder<?>> holders = new ArrayList<>(byId.size());
        for (Registered<?> registered : byId.values()) {
            holders.add(registered.holder());
        }
        return List.copyOf(holders);
    }

    public List<TableDefinition<?>> definitions() {
        List<TableDefinition<?>> definitions = new ArrayList<>(byId.size());
        for (Registered<?> registered : byId.values()) {
            definitions.add(registered.definition());
        }
        return List.copyOf(definitions);
    }

    /**
     * Перезагружает все таблицы из источника. Возвращает отчёты по каждой: успех (заменена) или ошибка
     * (прежняя версия в силе). Ни одна ошибка не прерывает загрузку остальных таблиц.
     */
    public List<Report> reload(ContentSource source) {
        Objects.requireNonNull(source, "Источник содержимого не может быть null: перезагружать не из чего");
        List<Report> reports = new ArrayList<>(byId.size());
        for (Registered<?> registered : byId.values()) {
            reports.add(registered.reload(source, clock));
        }
        return reports;
    }

    private record Registered<T>(TableDefinition<T> definition, TableHolder<T> holder) {
        Report reload(ContentSource source, Clock clock) {
            Optional<LoadedContent> content;
            try {
                content = source.read(definition.resourcePath());
            } catch (RuntimeException error) {
                String message = "чтение " + definition.resourcePath() + ": " + error.getMessage();
                holder.failed(message);
                return new Report(definition.id(), false, message);
            }
            if (content.isEmpty()) {
                return new Report(definition.id(), true, null);
            }
            LoadedContent loaded = content.get();
            try {
                T parsed = definition.parser().parse(loaded.json(), loaded.origin());
                holder.replace(parsed, loaded.source(), loaded.origin(), TableHolder.hashOf(loaded.json()), clock);
                return new Report(definition.id(), true, null);
            } catch (RuntimeException error) {
                String message = loaded.origin() + ": " + error.getMessage();
                holder.failed(message);
                return new Report(definition.id(), false, message);
            }
        }
    }

    /** Итог перезагрузки одной таблицы: обновлена ли версия и текст ошибки, если загрузка не удалась. */
    public record Report(String id, boolean updated, String error) {
        public boolean failed() {
            return error != null;
        }
    }
}