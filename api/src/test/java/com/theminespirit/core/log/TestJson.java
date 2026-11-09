package com.theminespirit.core.log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

final class TestJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private TestJson() {}

    static Map<String, Object> read(String line) {
        try {
            return MAPPER.readValue(line, MAP);
        } catch (IOException error) {
            throw new UncheckedIOException("Тест не прочитал JSON: " + line, error);
        }
    }
}
