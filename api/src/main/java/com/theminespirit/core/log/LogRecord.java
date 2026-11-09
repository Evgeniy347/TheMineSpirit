package com.theminespirit.core.log;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LogRecord {
    private final LogType type;
    private final long ts;
    private final long tick;
    private final String build;
    private final String source;
    private final Map<String, Object> fields;

    private LogRecord(LogType type, long ts, long tick, String build, String source, Map<String, Object> fields) {
        this.type = Objects.requireNonNull(type, "Тип записи лога не может быть null: без него не выбрать таблицу");
        this.ts = ts;
        this.tick = tick;
        this.build = Objects.requireNonNull(build, "Версия сборки не может быть null: без неё старый лог не отличить");
        this.source = Objects.requireNonNull(source, "Источник записи не может быть null: live или import");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        LogSchema.validate(type, ts, tick, build, source, this.fields);
    }

    public static Builder builder(LogType type, long ts, long tick, String build, String source) {
        return new Builder(type, ts, tick, build, source);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.json());
        map.put("ts", ts);
        map.put("tick", tick);
        map.put("build", build);
        map.put("source", source);
        map.putAll(fields);
        return map;
    }

    public String toJson() {
        return LogJson.write(asMap());
    }

    public static final class Builder {
        private final LogType type;
        private final long ts;
        private final long tick;
        private final String build;
        private final String source;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(LogType type, long ts, long tick, String build, String source) {
            this.type = type;
            this.ts = ts;
            this.tick = tick;
            this.build = build;
            this.source = source;
        }

        public Builder put(String name, Object value) {
            if (value != null) {
                fields.put(name, value);
            }
            return this;
        }

        public Builder spirit(String name, String id) {
            return put("spirit", name).put("spirit_id", id);
        }

        public Builder ticket(String ticket) {
            return put("ticket", ticket);
        }

        public Builder at(int x, int y, int z) {
            return put("x", x).put("y", y).put("z", z);
        }

        public Builder cause(LogCause cause) {
            return put("cause", cause.code());
        }

        public LogRecord build() {
            return new LogRecord(type, ts, tick, build, source, fields);
        }
    }
}
