package com.theminespirit.core.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PresentStacks {
    private PresentStacks() {}

    public static List<Stack> of(List<@Nullable Stack> slots) {
        Objects.requireNonNull(slots, "Слоты не могут быть null: пустой контейнер задают пустым списком");
        List<Stack> present = new ArrayList<>(slots.size());
        for (@Nullable Stack stack : slots) {
            if (stack != null) {
                present.add(stack);
            }
        }
        return List.copyOf(present);
    }
}
