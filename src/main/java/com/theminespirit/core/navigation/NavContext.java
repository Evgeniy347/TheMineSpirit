// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/pathing/movement/CalculationContext.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Objects;

public record NavContext(NavWorld world, NavSettings settings, int blocks) {
    public NavContext {
        Objects.requireNonNull(world, "Мир навигации не может быть null: по нему считаются ходы");
        Objects.requireNonNull(settings, "Настройки навигации не могут быть null: без них нет цен и сроков");
        if (blocks < 0) {
            throw new IllegalArgumentException("Блоков в запасе " + blocks + " меньше нуля");
        }
    }

    public boolean canPlace() {
        return settings.canPlace() && blocks > 0;
    }
}
