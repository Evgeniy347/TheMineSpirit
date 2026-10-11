package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class RestJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestJson() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать ответ REST в JSON: проверьте типы значений", e);
        }
    }
}
