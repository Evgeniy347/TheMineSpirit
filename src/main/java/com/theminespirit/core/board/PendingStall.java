package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class PendingStall {
    public static final long STALL_TICKS = 600;

    private final Map<String, Seen> seen = new HashMap<>();

    private record Seen(String reason, long since, boolean warned) {}

    public Optional<String> observe(String ticketId, @Nullable String reason, long now) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: застой считается на конкретное задание");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: застой идёт вперёд");
        }
        String key = reason == null ? "" : reason;
        Seen last = seen.get(ticketId);
        if (last == null || !last.reason().equals(key)) {
            seen.put(ticketId, new Seen(key, now, false));
            return Optional.empty();
        }
        if (last.warned() || now - last.since() < STALL_TICKS) {
            return Optional.empty();
        }
        seen.put(ticketId, new Seen(key, last.since(), true));
        return Optional.of(line(ticketId, key, last.since(), now));
    }

    public void retain(Set<String> pendingIds) {
        seen.keySet().retainAll(pendingIds);
    }

    public void forget(String ticketId) {
        seen.remove(ticketId);
    }

    static String line(String ticketId, String reason, long since, long now) {
        return "[board] PENDING " + ticketId + " дольше " + STALL_TICKS + " тиков с той же причиной: "
                + (reason.isEmpty() ? "—" : reason) + "; ждёт с тика " + since + ", сейчас " + now
                + " — проверьте, почему доска не выдаёт задание";
    }
}
