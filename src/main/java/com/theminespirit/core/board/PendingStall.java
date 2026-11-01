package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class PendingStall {
    public static final long STALL_TICKS = 600;

    private final long summaryTicks;
    private final Map<String, Seen> seen = new LinkedHashMap<>();
    private long lastSummary;
    private boolean summaryStarted;

    private record Seen(String reason, long since) {}

    public PendingStall() {
        this(BoardTuning.logSummaryTicks());
    }

    public PendingStall(long summaryTicks) {
        if (summaryTicks <= 0) {
            throw new IllegalArgumentException("Срок сводки " + summaryTicks + " должен быть положительным: иначе "
                    + "сводка превращается в спам");
        }
        this.summaryTicks = summaryTicks;
    }

    public void observe(String ticketId, @Nullable String reason, long now) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: застой считается на конкретное задание");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: застой идёт вперёд");
        }
        if (!summaryStarted) {
            summaryStarted = true;
            lastSummary = now;
        }
        String key = reason == null ? "" : reason;
        Seen last = seen.get(ticketId);
        if (last == null || !last.reason().equals(key)) {
            seen.put(ticketId, new Seen(key, now));
        }
    }

    public Optional<String> summary(long now) {
        if (seen.isEmpty()) {
            return Optional.empty();
        }
        if (now - lastSummary < summaryTicks) {
            return Optional.empty();
        }
        List<Map.Entry<String, Seen>> stalled = new ArrayList<>();
        for (Map.Entry<String, Seen> entry : seen.entrySet()) {
            if (now - entry.getValue().since() >= STALL_TICKS) {
                stalled.add(entry);
            }
        }
        lastSummary = now;
        if (stalled.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(line(stalled, now));
    }

    public void retain(Set<String> pendingIds) {
        seen.keySet().retainAll(pendingIds);
    }

    public void forget(String ticketId) {
        seen.remove(ticketId);
    }

    private String line(List<Map.Entry<String, Seen>> stalled, long now) {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (Map.Entry<String, Seen> entry : stalled) {
            String reason =
                    entry.getValue().reason().isEmpty() ? "—" : entry.getValue().reason();
            reasons.merge(reason, 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        reasons.forEach((reason, count) -> parts.add(count + "× " + reason));
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Seen> entry : stalled) {
            if (ids.size() == 5) {
                ids.add("и ещё " + (stalled.size() - 5));
                break;
            }
            ids.add(entry.getKey() + " с тика " + entry.getValue().since());
        }
        return "[board] PENDING дольше " + STALL_TICKS + " тиков: " + stalled.size()
                + " заданий с той же причиной; причины: " + String.join("; ", parts)
                + "; ждут: " + String.join(", ", ids) + "; сейчас " + now
                + " — проверьте, почему доска не выдаёт задание";
    }
}
