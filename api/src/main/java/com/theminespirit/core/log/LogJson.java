package com.theminespirit.core.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class LogJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LogJson() {}

    public static String write(Map<String, Object> record) {
        return writeValue(record);
    }

    public static String writeValue(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Запись лога не превращается в JSON: " + error.getOriginalMessage(), error);
        }
    }
}
