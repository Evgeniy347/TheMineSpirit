package com.theminespirit.core.script;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ScriptCatalog {
    private final SpecCatalog<ScriptSpec> delegate;

    private ScriptCatalog(SpecCatalog<ScriptSpec> delegate) {
        this.delegate = delegate;
    }

    public static ScriptCatalog of(List<ScriptSpec> scripts) {
        Objects.requireNonNull(scripts, "scripts не может быть null: пустой каталог задают пустым списком");
        return new ScriptCatalog(SpecCatalog.of(
                scripts, ScriptSpec::id, "Дублирующийся id скрипта '{id}': каталог должен находить скрипт однозначно"));
    }

    public Optional<ScriptSpec> find(String id) {
        return delegate.find(id);
    }

    public List<ScriptSpec> all() {
        return delegate.all();
    }

    public ScriptCatalog restrictedTo(Set<String> ids) {
        Objects.requireNonNull(ids, "ids не может быть null: для отбора нужен набор ключей");
        return new ScriptCatalog(delegate.restrictedTo(
                ids, "Скрипт '{id}' вызывается адаптером, но не описан в каталоге: сверьте наличие описания"));
    }
}
