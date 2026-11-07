package com.theminespirit.core.board;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class HoldBook {
    private final Map<String, String> reasons = new LinkedHashMap<>();

    public void hold(String ticketId, String reason) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: держание адресно");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Причина держания задания '" + ticketId + "' пуста: "
                    + "наблюдателю нужна причина, иначе задание выглядит забытым");
        }
        reasons.put(ticketId, reason);
    }

    public void release(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: держание снимается с задания");
        reasons.remove(ticketId);
    }

    public boolean held(String ticketId) {
        return reasons.containsKey(ticketId);
    }

    public Optional<String> reason(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: причина читается у задания");
        return Optional.ofNullable(reasons.get(ticketId));
    }

    void retain(Collection<String> ticketIds) {
        Objects.requireNonNull(ticketIds, "Список заданий не может быть null: пустой задают пустым");
        reasons.keySet().retainAll(ticketIds);
    }

    void clear() {
        reasons.clear();
    }
}
