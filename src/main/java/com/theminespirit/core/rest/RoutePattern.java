package com.theminespirit.core.rest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RoutePattern {
    private static final Pattern PARAM_VALUE = Pattern.compile("[A-Za-z0-9_:,@-]{1,64}");
    private static final Pattern PARAM_SEGMENT = Pattern.compile("[{]([a-z][A-Za-z0-9]*)[}]");

    private final List<String> segments;

    private RoutePattern(List<String> segments) {
        this.segments = segments;
    }

    static RoutePattern parse(String pattern) {
        Optional<List<String>> segments = split(pattern);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Шаблон маршрута '" + pattern + "' неверен: он начинается с '/', "
                    + "не заканчивается '/' и не содержит '//', пример — /v1/bots/{id}");
        }
        for (String segment : segments.get()) {
            if ((segment.contains("{") || segment.contains("}"))
                    && !PARAM_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("Сегмент '" + segment + "' шаблона '" + pattern
                        + "' неверен: параметр пути записывается целым сегментом {имя}, имя — латиница с малой буквы");
            }
        }
        return new RoutePattern(segments.get());
    }

    static Optional<List<String>> split(String path) {
        if (!path.startsWith("/")) {
            return Optional.empty();
        }
        if ("/".equals(path)) {
            return Optional.of(List.of());
        }
        List<String> segments = new ArrayList<>(Arrays.asList(path.substring(1).split("/", -1)));
        if (segments.stream().anyMatch(String::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(segments);
    }

    Optional<PathParams> match(List<String> pathSegments) {
        if (segments.size() != pathSegments.size()) {
            return Optional.empty();
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            String expected = segments.get(i);
            String actual = pathSegments.get(i);
            Matcher parameter = PARAM_SEGMENT.matcher(expected);
            if (parameter.matches()) {
                Optional<String> decoded = decodeParameter(actual);
                if (decoded.isEmpty()
                        || !PARAM_VALUE.matcher(decoded.orElseThrow()).matches()) {
                    return Optional.empty();
                }
                params.put(parameter.group(1), decoded.orElseThrow());
            } else if (!expected.equals(actual)) {
                return Optional.empty();
            }
        }
        return Optional.of(new PathParams(params));
    }

    private static Optional<String> decodeParameter(String raw) {
        try {
            return Optional.of(URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }
}
