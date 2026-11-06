package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

public final class IdempotencyCache {
    public static final int CAPACITY = 1024;

    private static final ObjectMapper CANONICAL =
            new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public sealed interface Result {
        record New() implements Result {}

        record Replay(String storedResponse) implements Result {}

        record Conflict() implements Result {}
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Duration waitTimeout;

    public IdempotencyCache() {
        this(RestTuning.IDEMPOTENCY_WAIT);
    }

    IdempotencyCache(Duration waitTimeout) {
        this.waitTimeout = Objects.requireNonNull(
                waitTimeout, "Предел ожидания повтора не может быть null: без него ожидание бесконечно");
        if (waitTimeout.isZero() || waitTimeout.isNegative()) {
            throw new IllegalArgumentException("Предел ожидания повтора " + waitTimeout
                    + " обязан быть положительным: иначе дубликат не дождётся");
        }
    }

    public synchronized Result check(String requestId, String fingerprint) {
        Objects.requireNonNull(requestId, "requestId не может быть null: идемпотентность держится на UUID клиента");
        Objects.requireNonNull(fingerprint, "fingerprint не может быть null: сравнение тел требует отпечатка");
        Entry entry = entries.get(requestId);
        if (entry == null) {
            entries.put(requestId, Entry.claimed(fingerprint));
            return new Result.New();
        }
        if (!entry.fingerprint.equals(fingerprint)) {
            return new Result.Conflict();
        }
        long deadline = System.nanoTime() + waitTimeout.toNanos();
        while (entry.inProgress()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new ApiException(
                        ErrorCode.INTERNAL,
                        "Повтор request_id " + requestId + " не дождался ответа первого запроса за "
                                + ServerThreadCall.formatSeconds(waitTimeout) + " с: первый запрос завис или упал, "
                                + "не сохранив ответ; повторите запрос позже",
                        Map.of("request_id", requestId, "timeout_ms", waitTimeout.toMillis()));
            }
            try {
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ApiException(
                        ErrorCode.INTERNAL,
                        "Ожидание идемпотентного повтора request_id " + requestId + " прервано: мост останавливается",
                        Map.of("request_id", requestId));
            }
            entry = entries.get(requestId);
            if (entry == null) {
                entries.put(requestId, Entry.claimed(fingerprint));
                return new Result.New();
            }
            if (!entry.fingerprint.equals(fingerprint)) {
                return new Result.Conflict();
            }
        }
        return new Result.Replay(entry.stored());
    }

    public synchronized void remember(String requestId, String fingerprint, String response) {
        Objects.requireNonNull(requestId, "requestId не может быть null: идемпотентность держится на UUID клиента");
        Objects.requireNonNull(fingerprint, "fingerprint не может быть null: сравнение тел требует отпечатка");
        Objects.requireNonNull(response, "response не может быть null: повтор обязан вернуть прежний ответ дословно");
        Entry entry = entries.get(requestId);
        if (entry == null) {
            entries.put(requestId, new Entry(fingerprint, response));
        } else if (entry.inProgress()) {
            entry.complete(response);
        }
        while (entries.size() > CAPACITY) {
            entries.remove(entries.keySet().iterator().next());
        }
        notifyAll();
    }

    public synchronized void release(String requestId) {
        Entry entry = entries.get(requestId);
        if (entry != null && entry.inProgress()) {
            entries.remove(requestId);
        }
        notifyAll();
    }

    public synchronized int size() {
        return entries.size();
    }

    public static String fingerprint(
            String bot, @Nullable String goal, @Nullable String planJson, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bot", bot);
        body.put("goal", goal);
        body.put("plan", planJson == null ? null : readPlan(planJson));
        body.put("params", params == null ? Map.of() : params);
        return fingerprintOf(body);
    }

    public static String fingerprintOf(Map<String, Object> body) {
        try {
            String canonical = CANONICAL.writeValueAsString(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Тело запроса не сериализуется в канонический JSON для отпечатка: "
                            + "проверьте типы значений params",
                    e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 недоступен в этой JVM: идемпотентность REST не может " + "сравнивать тела запросов", e);
        }
    }

    private static Object readPlan(String planJson) {
        try {
            return CANONICAL.readValue(planJson, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("План для отпечатка не разобран как JSON: " + planJson, e);
        }
    }

    private static final class Entry {
        private final String fingerprint;
        private @Nullable String response;

        Entry(String fingerprint, @Nullable String response) {
            this.fingerprint = fingerprint;
            this.response = response;
        }

        static Entry claimed(String fingerprint) {
            return new Entry(fingerprint, null);
        }

        boolean inProgress() {
            return response == null;
        }

        String stored() {
            String value = response;
            if (value == null) {
                throw new IllegalStateException("Повтор запроса читает ответ, которого ещё нет: ждать надо, пока "
                        + "первый запрос не сохранит ответ (inProgress)");
            }
            return value;
        }

        void complete(String stored) {
            response = stored;
        }
    }
}
