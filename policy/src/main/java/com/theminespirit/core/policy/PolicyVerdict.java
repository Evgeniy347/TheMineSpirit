package com.theminespirit.core.policy;

import java.util.Objects;

public sealed interface PolicyVerdict {
    record Clear() implements PolicyVerdict {}

    record Reflex(PolicyId policy, ReflexAction action, String reason) implements PolicyVerdict {
        public Reflex {
            Objects.requireNonNull(policy, "Политика рефлекса не может быть null: по ней выбирается исключение и лог");
            Objects.requireNonNull(action, "Действие рефлекса не может быть null: исполнителю нечего делать");
            Objects.requireNonNull(reason, "Причина рефлекса не может быть null: по ней читается лог и итог задачи");
            if (reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Причина рефлекса пуста: лог «дух побежал» без причины нельзя разобрать");
            }
        }
    }
}
