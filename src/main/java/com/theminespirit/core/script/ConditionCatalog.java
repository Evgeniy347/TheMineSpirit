package com.theminespirit.core.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ConditionCatalog {
    private final Map<String, ConditionSpec> byId;

    private ConditionCatalog(Map<String, ConditionSpec> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static ConditionCatalog empty() {
        return of(List.of());
    }

    public static ConditionCatalog of(List<ConditionSpec> conditions) {
        Objects.requireNonNull(conditions, "conditions не может быть null: пустой каталог задают пустым списком");
        Map<String, ConditionSpec> byId = new LinkedHashMap<>();
        for (ConditionSpec condition : conditions) {
            Objects.requireNonNull(condition, "Каталог не может содержать null: каждый элемент — описание проверки");
            ConditionSpec previous = byId.putIfAbsent(condition.id(), condition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Дублирующийся id проверки '" + condition.id() + "': каталог должен находить проверку однозначно");
            }
        }
        return new ConditionCatalog(byId);
    }

    public Optional<ConditionSpec> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<ConditionSpec> all() {
        return List.copyOf(byId.values());
    }

    public ConditionCatalog restrictedTo(Set<String> ids) {
        Objects.requireNonNull(ids, "ids не может быть null: для отбора нужен набор ключей");
        List<ConditionSpec> selected = new ArrayList<>();
        for (String id : ids) {
            ConditionSpec spec = byId.get(id);
            if (spec == null) {
                throw new IllegalArgumentException(
                        "Проверка '" + id + "' вызывается адаптером, но не описана в каталоге: сверьте наличие описания");
            }
            selected.add(spec);
        }
        return of(selected);
    }
}
