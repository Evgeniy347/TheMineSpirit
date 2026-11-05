package com.theminespirit.core.inventory;

import java.util.Objects;

public sealed interface ContainerChoice permits ContainerChoice.Chosen, ContainerChoice.Refused {
    record Chosen(int index) implements ContainerChoice {
        public Chosen {
            if (index < 0) {
                throw new IllegalArgumentException("Индекс контейнера " + index
                        + " отрицательный: контейнеры нумеруются с нуля, отрицательного не существует");
            }
        }
    }

    record Refused(String reason) implements ContainerChoice {
        public Refused {
            Objects.requireNonNull(
                    reason, "Причина отказа не может быть null: без причины непонятно, почему некуда класть");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("Причина отказа пуста: без причины непонятно, почему некуда класть");
            }
        }
    }
}
