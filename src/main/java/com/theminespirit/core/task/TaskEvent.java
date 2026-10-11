package com.theminespirit.core.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TaskEvent(long id, String type, Map<String, Object> data) {
    public static final String TYPE_STATE = "state";
    public static final String TYPE_PROGRESS = "progress";
    public static final String TYPE_SCRIPT_FAILED = "script_failed";

    public TaskEvent {
        Objects.requireNonNull(type, "type не может быть null: тип кадра выбирает дифф событий");
        Objects.requireNonNull(data, "data не может быть null: пустые данные задают пустой картой");
        data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public TaskEvent withId(long assignedId) {
        return new TaskEvent(assignedId, type, data);
    }
}
