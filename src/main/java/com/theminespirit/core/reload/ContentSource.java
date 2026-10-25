package com.theminespirit.core.reload;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@FunctionalInterface
public interface ContentSource {
    Optional<LoadedContent> read(String resourcePath);

    static ContentSource of(Map<String, LoadedContent> content) {
        Objects.requireNonNull(content, "Карта содержимого не может быть null: тестовый источник без неё пуст");
        Map<String, LoadedContent> copy = Map.copyOf(content);
        return resourcePath -> Optional.ofNullable(copy.get(resourcePath));
    }
}
