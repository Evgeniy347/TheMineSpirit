package com.theminespirit.core.script;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ConditionCatalog {
    private final SpecCatalog<ConditionSpec> delegate;

    private ConditionCatalog(SpecCatalog<ConditionSpec> delegate) {
        this.delegate = delegate;
    }

    public static ConditionCatalog empty() {
        return of(List.of());
    }

    public static ConditionCatalog of(List<ConditionSpec> conditions) {
        Objects.requireNonNull(conditions, "conditions не может быть null: пустой каталог задают пустым списком");
        return new ConditionCatalog(SpecCatalog.of(conditions, ConditionSpec::id,
                "Дублирующийся id проверки '{id}': каталог должен находить проверку однозначно"));
    }

    public Optional<ConditionSpec> find(String id) {
        return delegate.find(id);
    }

    public List<ConditionSpec> all() {
        return delegate.all();
    }

    public ConditionCatalog restrictedTo(Set<String> ids) {
        Objects.requireNonNull(ids, "ids не может быть null: для отбора нужен набор ключей");
        return new ConditionCatalog(delegate.restrictedTo(ids,
                "Проверка '{id}' вызывается адаптером, но не описана в каталоге: сверьте наличие описания"));
    }
}
