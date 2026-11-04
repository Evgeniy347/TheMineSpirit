package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class GraphFailureMemory {
    public static final long WINDOW_TICKS = 6000L;
    private static final int MAX_COUNT = 999;
    private static final int MAX_ENTRIES = 128;
    private static final char KEY_SEPARATOR = '\u0000';

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private record Entry(String node, String target, int count, GraphFeedback.Cause cause, long lastTick) {}

    public void note(String node, String target, @Nullable String reason, long now) {
        Objects.requireNonNull(node, "Узел провала не может быть null: память адресная");
        Objects.requireNonNull(target, "Цель провала не может быть null: неизвестную задают пустой строкой");
        if (now < 0) {
            throw new IllegalArgumentException("Тик провала " + now + " отрицателен: время идёт вперёд");
        }
        prune(now);
        String key = node + KEY_SEPARATOR + target;
        Entry previous = entries.get(key);
        int count = previous == null ? 1 : Math.min(MAX_COUNT, previous.count() + 1);
        entries.put(key, new Entry(node, target, count, GraphFeedback.Cause.of(reason), now));
        while (entries.size() > MAX_ENTRIES) {
            Iterator<String> oldest = entries.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    public List<GraphFeedback.Failure> facts(long now) {
        prune(now);
        List<GraphFeedback.Failure> facts = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            facts.add(new GraphFeedback.Failure(
                    entry.node(), entry.target(), entry.count(), entry.cause(), entry.lastTick()));
        }
        facts.sort((one, two) -> {
            int byTime = Long.compare(two.lastTick(), one.lastTick());
            return byTime != 0 ? byTime : one.node().compareTo(two.node());
        });
        return List.copyOf(facts);
    }

    public Optional<GraphFeedback.Failure> recent(String node, String target, long now) {
        prune(now);
        Entry entry = entries.get(node + KEY_SEPARATOR + target);
        return entry == null
                ? Optional.empty()
                : Optional.of(new GraphFeedback.Failure(
                        entry.node(), entry.target(), entry.count(), entry.cause(), entry.lastTick()));
    }

    public int count(String node, long now) {
        prune(now);
        int total = 0;
        for (Entry entry : entries.values()) {
            if (entry.node().equals(node)) {
                total += entry.count();
            }
        }
        return total;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private void prune(long now) {
        long oldest = now - WINDOW_TICKS;
        entries.values().removeIf(entry -> entry.lastTick() <= oldest);
    }

    public static @Nullable String node(String ticketId, @Nullable String cause) {
        String raw = ticketId.startsWith(GraphTickets.PREFIX) ? ticketId : cause;
        if (raw == null || !raw.startsWith(GraphTickets.PREFIX)) {
            return null;
        }
        String node = raw.substring(GraphTickets.PREFIX.length());
        int at = node.indexOf('@');
        if (at >= 0) {
            node = node.substring(0, at);
        }
        if (node.endsWith(":2")) {
            node = node.substring(0, node.length() - 2);
        }
        return node.isBlank() ? null : node;
    }

    public static String target(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
