package com.theminespirit.core.reload;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record TableStatus(
        String id,
        Source source,
        String origin,
        Instant loadedAt,
        String hash,
        @Nullable String error) {
    public enum Source {
        MOD("мод"),
        DATAPACK("датапак"),
        BUILTIN("встроенный");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public TableStatus {
        Objects.requireNonNull(id, "id таблицы не может быть null: по нему статус находят в списке /spirit tables");
        Objects.requireNonNull(source, "Источник таблицы не может быть null: без него не отличить мод от датапака");
        Objects.requireNonNull(loadedAt, "Время загрузки не может быть null: его печатает /spirit tables");
    }

    public boolean failed() {
        return error != null;
    }
}
