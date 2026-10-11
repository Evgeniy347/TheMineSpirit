package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IdempotencyCache {
    public static final int CAPACITY = 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public sealed interface Result {
        record New() implements Result {
        }

        record Replay(String storedResponse) implements Result {
        }

        record Conflict() implements Result {
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public synchronized Result check(String requestId, String fingerprint) {
        Objects.requireNonNull(requestId, "requestId не может быть null: идемпотентность держится на UUID клиента");
        Objects.requireNonNull(fingerprint, "fingerprint не может быть null: сравнение тел требует отпечатка");
        Entry entry = entries.get(requestId);
        if (entry == null) {
            return new Result.New();
        }
        if (!entry.fingerprint.equals(fingerprint)) {
            return new Result.Conflict();
        }
        return new Result.Replay(entry.response);
    }

    public synchronized void remember(String requestId, String fingerprint, String response) {
        Objects.requireNonNull(requestId, "requestId не может быть null: идемпотентность держится на UUID клиента");
        Objects.requireNonNull(fingerprint, "fingerprint не может быть null: сравнение тел требует отпечатка");
        Objects.requireNonNull(response, "response не может быть null: повтор обязан вернуть прежний ответ дословно");
        if (!entries.containsKey(requestId)) {
            entries.put(requestId, new Entry(fingerprint, response));
            while (entries.size() > CAPACITY) {
                entries.remove(entries.keySet().iterator().next());
            }
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    public static String fingerprint(String bot, String goal, String planJson, Map<String, Object> params) {
        String canonical = canonicalPart(bot) + '\n' + canonicalPart(goal) + '\n' + canonicalPart(planJson)
                + '\n' + canonicalParams(params);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в этой JVM: идемпотентность REST не может "
                    + "сравнивать тела запросов", e);
        }
    }

    private static String canonicalPart(String value) {
        return value == null ? "" : value;
    }

    private static String canonicalParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        List<String> names = new ArrayList<>(params.keySet());
        names.sort(Comparator.naturalOrder());
        StringBuilder canonical = new StringBuilder("{");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                canonical.append(',');
            }
            canonical.append(quote(names.get(i))).append(':').append(canonicalValue(params.get(names.get(i))));
        }
        return canonical.append('}').toString();
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> asStringKeys = new LinkedHashMap<>();
            nested.forEach((key, item) -> asStringKeys.put(String.valueOf(key), item));
            return canonicalParams(asStringKeys);
        }
        return String.valueOf(value);
    }

    private static String quote(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Строка параметра не сериализуется в канонический JSON: " + text, e);
        }
    }

    private static final class Entry {
        private final String fingerprint;
        private final String response;

        Entry(String fingerprint, String response) {
            this.fingerprint = fingerprint;
            this.response = response;
        }
    }
}
