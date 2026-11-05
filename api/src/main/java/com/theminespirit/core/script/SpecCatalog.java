package com.theminespirit.core.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class SpecCatalog<T> {
    private final Map<String, T> byId;
    private final Function<T, String> idGetter;

    private SpecCatalog(Map<String, T> byId, Function<T, String> idGetter) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.idGetter = Objects.requireNonNull(idGetter);
    }

    public static <T> SpecCatalog<T> of(List<T> specs, Function<T, String> idGetter, String duplicateMessage) {
        Objects.requireNonNull(specs, "specs не может быть null: пустой каталог задают пустым списком");
        Objects.requireNonNull(idGetter, "idGetter не может быть null");
        Objects.requireNonNull(duplicateMessage, "duplicateMessage не может быть null");
        Map<String, T> byId = new LinkedHashMap<>();
        for (T spec : specs) {
            Objects.requireNonNull(spec, "Каталог не может содержать null: каждый элемент — описание");
            String id = idGetter.apply(spec);
            T previous = byId.putIfAbsent(id, spec);
            if (previous != null) {
                throw new IllegalArgumentException(duplicateMessage.replace("{id}", id));
            }
        }
        return new SpecCatalog<>(byId, idGetter);
    }

    public Optional<T> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<T> all() {
        return List.copyOf(byId.values());
    }

    public SpecCatalog<T> restrictedTo(Set<String> ids, String missingMessage) {
        Objects.requireNonNull(ids, "ids не может быть null: для отбора нужен набор ключей");
        Objects.requireNonNull(missingMessage, "missingMessage не может быть null");
        for (String id : ids) {
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException(missingMessage.replace("{id}", id));
            }
        }
        Map<String, T> selected = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : byId.entrySet()) {
            if (ids.contains(entry.getKey())) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return new SpecCatalog<>(selected, idGetter);
    }
}
