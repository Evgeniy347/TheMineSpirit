package com.theminespirit.core.reload;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class TableRegistry {
    private final Map<String, Registered<?>> byId = new LinkedHashMap<>();
    private final Clock clock;

    public TableRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Часы не могут быть null: по ним штампуется время загрузки");
    }

    public <T> TableHolder<T> register(TableDefinition<T> definition, T initial) {
        Objects.requireNonNull(initial, "Начальная версия не может быть null: читатель не должен видеть пустоту");
        return registerLazy(definition, () -> initial, TableStatus.Source.BUILTIN, "classpath");
    }

    public <T> TableHolder<T> registerLazy(TableDefinition<T> definition, Supplier<T> seed) {
        return registerLazy(definition, seed, TableStatus.Source.MOD, "classpath (мод)");
    }

    private <T> TableHolder<T> registerLazy(
            TableDefinition<T> definition, Supplier<T> seed, TableStatus.Source source, String origin) {
        Objects.requireNonNull(definition, "Определение таблицы не может быть null: регистрировать нечего");
        Objects.requireNonNull(seed, "Источник начальной версии не может быть null: читатель не должен видеть пустоту");
        if (byId.containsKey(definition.id())) {
            throw new IllegalStateException("Таблица с id '" + definition.id()
                    + "' уже зарегистрирована: два держателя на одну таблицу разошлись бы версиями");
        }
        Registered<T> registered = new Registered<>(definition, seed, source, origin, clock);
        byId.put(definition.id(), registered);
        return registered.holder();
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

    public List<Report> reload(ContentSource source) {
        Objects.requireNonNull(source, "Источник содержимого не может быть null: перезагружать не из чего");
        List<Report> reports = new ArrayList<>(byId.size());
        for (Registered<?> registered : byId.values()) {
            reports.add(registered.reload(source, clock));
        }
        return reports;
    }

    private static final class Registered<T> {
        private final TableDefinition<T> definition;
        private final Supplier<T> seed;
        private final TableStatus.Source seedSource;
        private final String seedOrigin;
        private final Clock clock;
        private volatile @Nullable TableHolder<T> holder;

        Registered(
                TableDefinition<T> definition,
                Supplier<T> seed,
                TableStatus.Source seedSource,
                String seedOrigin,
                Clock clock) {
            this.definition = definition;
            this.seed = seed;
            this.seedSource = seedSource;
            this.seedOrigin = seedOrigin;
            this.clock = clock;
        }

        TableDefinition<T> definition() {
            return definition;
        }

        synchronized TableHolder<T> holder() {
            if (holder == null) {
                T value = seed.get();
                holder = new TableHolder<>(
                        definition.id(),
                        value,
                        seedSource,
                        seedOrigin,
                        TableHolder.hashOf(String.valueOf(value)),
                        clock);
            }
            return holder;
        }

        Report reload(ContentSource source, Clock reloadClock) {
            TableHolder<T> target = holder();
            Optional<List<LoadedContent>> content;
            try {
                content = definition.read(source);
            } catch (RuntimeException error) {
                String message = "чтение " + definition.resourcePaths() + ": " + error.getMessage();
                target.failed(message);
                return new Report(definition.id(), false, message);
            }
            if (content.isEmpty()) {
                return new Report(definition.id(), true, null);
            }
            List<LoadedContent> loaded = content.get();
            try {
                T parsed = definition.parser().parse(loaded);
                LoadedContent first = loaded.get(0);
                target.replace(parsed, first.source(), first.origin(), TableHolder.hashOf(join(loaded)), reloadClock);
                return new Report(definition.id(), true, null);
            } catch (RuntimeException error) {
                String message = loaded.get(0).origin() + ": " + error.getMessage();
                target.failed(message);
                return new Report(definition.id(), false, message);
            }
        }

        private static String join(List<LoadedContent> loaded) {
            StringBuilder text = new StringBuilder();
            for (LoadedContent content : loaded) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(content.json());
            }
            return text.toString();
        }
    }

    public record Report(
            String id, boolean updated, @Nullable String error) {
        public boolean failed() {
            return error != null;
        }
    }
}
