package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class TransferWaitNotes {
    private final Map<String, String> seen = new HashMap<>();

    public Optional<String> observe(String ticketId, String state) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: ожидание считается на конкретную передачу");
        Objects.requireNonNull(state, "Состояние передачи не может быть null: пустое задают пустой строкой");
        if (state.equals(seen.put(ticketId, state))) {
            return Optional.empty();
        }
        return Optional.of(line(ticketId, state));
    }

    public void retain(Set<String> openIds) {
        seen.keySet().retainAll(openIds);
    }

    private static String line(String ticketId, String state) {
        return "[board] задание " + ticketId + " ждёт передачу ингредиентов: " + state;
    }
}
