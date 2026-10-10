package com.theminespirit.core.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ScriptCatalog {
    private final Map<String, ScriptSpec> byId;

    private ScriptCatalog(Map<String, ScriptSpec> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static ScriptCatalog of(List<ScriptSpec> scripts) {
        Objects.requireNonNull(scripts, "scripts не может быть null: пустой каталог задают пустым списком");
        Map<String, ScriptSpec> byId = new LinkedHashMap<>();
        for (ScriptSpec script : scripts) {
            Objects.requireNonNull(script, "Каталог не может содержать null: каждый элемент — описание скрипта");
            ScriptSpec previous = byId.putIfAbsent(script.id(), script);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Дублирующийся id скрипта '" + script.id() + "': каталог должен находить скрипт однозначно");
            }
        }
        return new ScriptCatalog(byId);
    }

    public Optional<ScriptSpec> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<ScriptSpec> all() {
        return List.copyOf(byId.values());
    }
}
