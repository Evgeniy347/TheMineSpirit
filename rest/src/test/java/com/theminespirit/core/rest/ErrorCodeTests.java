package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorCodeTests {
    @Test
    void errorCodes_AllValues_MatchDocumentedCodeAndStatusTable() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("invalid_request", 400);
        expected.put("forbidden", 403);
        expected.put("not_found", 404);
        expected.put("conflict", 409);
        expected.put("invalid_plan", 422);
        expected.put("rate_limited", 429);
        expected.put("internal", 500);

        Map<String, Integer> actual = new LinkedHashMap<>();
        for (ErrorCode code : ErrorCode.values()) {
            actual.put(code.wire(), code.status());
        }

        assertEquals(
                expected,
                actual,
                "Таблица «код ошибки → HTTP-статус» — контракт docs/api/README.md: клиент выбирает поведение по коду и "
                        + "статусу. Кода unauthorized нет (авторизации нет). Меняя таблицу осознанно, правь и docs/api/README.md");
    }
}
