package com.theminespirit.core.rest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QueryString {
    private QueryString() {}

    public static Map<String, String> parse(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            query.putIfAbsent(decode(name, raw), decode(value, raw));
        }
        return query;
    }

    private static String decode(String part, String raw) {
        try {
            return URLDecoder.decode(part, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Параметры запроса '" + raw + "' не разобраны: неверная процентная запись в '" + part
                            + "'. Проверьте кодирование символов %XX",
                    Map.of("query", raw),
                    e);
        }
    }
}
