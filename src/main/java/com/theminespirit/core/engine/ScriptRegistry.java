package com.theminespirit.core.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ScriptRegistry {
    private final Map<String, ScriptFactory> byId;

    private ScriptRegistry(Map<String, ScriptFactory> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static ScriptRegistry of(Map<String, ScriptFactory> factories) {
        Objects.requireNonNull(factories, "factories не может быть null: пустой реестр задают пустой картой");
        Map<String, ScriptFactory> byId = new LinkedHashMap<>();
        for (Map.Entry<String, ScriptFactory> entry : factories.entrySet()) {
            String id = Objects.requireNonNull(entry.getKey(),
                    "id скрипта не может быть null: по id движок находит реализацию");
            ScriptFactory factory = Objects.requireNonNull(entry.getValue(),
                    "фабрика скрипта '" + id + "' не может быть null: без неё движок не создаст экземпляр");
            byId.put(id, factory);
        }
        return new ScriptRegistry(byId);
    }

    public Optional<ScriptFactory> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean contains(String id) {
        return byId.containsKey(id);
    }
}
