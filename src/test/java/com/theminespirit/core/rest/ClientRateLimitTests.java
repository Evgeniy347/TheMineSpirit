package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ClientRateLimitTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long SECOND = 1_000_000_000L;
    private static final String CLIENT = "192.168.0.10";

    @Test
    void reject_BurstOfHundred_AllowsFortyThenAnswers429() {
        ClientRateLimit limit = new ClientRateLimit();

        int allowed = 0;
        int rejected = 0;
        for (int i = 0; i < 100; i++) {
            if (limit.reject(CLIENT, 0).isEmpty()) {
                allowed++;
            } else {
                rejected++;
            }
        }

        assertEquals(40, allowed, "Ведро адреса вмещает 40 запросов подряд");
        assertEquals(60, rejected, "Остальные 60 запросов всплеска обязаны получить отказ 429");
    }

    @Test
    void reject_OverLimit_Returns429RateLimitedWithRetryAfterHeaderAndBody() throws Exception {
        ClientRateLimit limit = new ClientRateLimit();
        for (int i = 0; i < 40; i++) {
            limit.reject(CLIENT, 0);
        }

        RestResponse response = limit.reject(CLIENT, 0).orElseThrow();

        assertEquals(429, response.status(), "Отказ лимитера обязан быть HTTP 429");
        assertEquals("1", response.headers().get("Retry-After"),
                "Заголовок Retry-After обязан сообщать целые секунды ожидания (минимум 1): клиент решает, когда повторить");
        JsonNode error = MAPPER.readTree(response.body()).path("error");
        assertEquals("rate_limited", error.path("code").asText(), "Код ошибки обязан быть rate_limited");
        assertEquals(1, error.path("details").path("retry_after_seconds").asInt(),
                "details обязан дублировать время ожидания для клиентов, не читающих заголовки");
        assertTrue(error.path("message").asText().contains("40"),
                "Сообщение обязано называть лимит, чтобы клиент понял, что превышено: " + response.body());
    }

    @Test
    void reject_AfterWaiting_AllowsAgainAtRefillRate() {
        ClientRateLimit limit = new ClientRateLimit();
        for (int i = 0; i < 40; i++) {
            limit.reject(CLIENT, 0);
        }

        int allowed = 0;
        for (int i = 0; i < 20; i++) {
            if (limit.reject(CLIENT, SECOND / 2).isEmpty()) {
                allowed++;
            }
        }

        assertEquals(10, allowed,
                "За полсекунды ведро пополняется на 10 запросов (20 в секунду): ровно столько обязано пройти");
    }

    @Test
    void reject_OtherClient_NotAffectedByNeighbourBurst() {
        ClientRateLimit limit = new ClientRateLimit();
        for (int i = 0; i < 100; i++) {
            limit.reject(CLIENT, 0);
        }

        assertFalse(limit.reject("192.168.0.11", 0).isPresent(),
                "Исчерпанное ведро одного адреса не должно блокировать соседний адрес");
    }
}
