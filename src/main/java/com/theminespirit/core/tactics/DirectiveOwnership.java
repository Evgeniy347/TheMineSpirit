package com.theminespirit.core.tactics;

import java.util.Objects;
import java.util.Optional;

public final class DirectiveOwnership {
    private DirectiveOwner owner;

    public void appliedBy(DirectiveOwner newOwner) {
        owner = Objects.requireNonNull(newOwner, "У директивы обязан быть владелец: иначе её снимет первый встречный");
    }

    public boolean mayClear(DirectiveOwner requester) {
        Objects.requireNonNull(requester, "Снятие директивы без владельца не проверить: кто снимает — часть правила");
        return owner == requester;
    }

    public void cleared() {
        owner = null;
    }

    public Optional<DirectiveOwner> owner() {
        return Optional.ofNullable(owner);
    }
}
