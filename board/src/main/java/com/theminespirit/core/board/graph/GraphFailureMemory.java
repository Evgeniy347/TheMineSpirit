package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

public final class GraphFailureMemory {
    public static final double DEFAULT_HALF_LIFE_TICKS = 3000.0;
    private static final int MAX_COUNT = 999;
    private static final int MAX_ENTRIES = 128;
    private static final int MAX_TICKS = 32;
    private static final double MIN_WEIGHT = 1e-3;
    private static final char KEY_SEPARATOR = '\u0000';

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private record Entry(
            String node, String target, GraphFeedback.Cause cause, long lastTick, List<Long> ticks, int count) {
        double weight(long now, double halfLife) {
            double total = 0.0;
            for (long tick : ticks) {
                total += Math.pow(0.5, (now - tick) / halfLife);
            }
            int older = count - ticks.size();
            if (older > 0) {
                long oldest = ticks.isEmpty() ? lastTick : ticks.get(0);
                total += older * Math.pow(0.5, (now - oldest) / halfLife);
            }
            return total;
        }
    }

    public void note(String node, String target, @Nullable String reason, long now) {
        Objects.requireNonNull(node, "Узел провала не может быть null: память адресная");
        Objects.requireNonNull(target, "Цель провала не может быть null: неизвестную задают пустой строкой");
        requireTick(now, "провала");
        Entry previous = entries.get(node + KEY_SEPARATOR + target);
        GraphFeedback.Cause cause = GraphFeedback.Cause.of(reason);
        Entry next = previous == null
                ? new Entry(node, target, cause, now, List.of(now), 1)
                : new Entry(
                        previous.node(),
                        previous.target(),
                        cause,
                        now,
                        withTick(previous.ticks(), now),
                        Math.min(MAX_COUNT, previous.count() + 1));
        entries.put(node + KEY_SEPARATOR + target, next);
        trim();
    }

    public void succeed(String node, String target, long now) {
        Objects.requireNonNull(node, "Узел успеха не может быть null: память адресная");
        Objects.requireNonNull(target, "Цель успеха не может быть null: неизвестную задают пустой строкой");
        requireTick(now, "успеха");
        entries.remove(node + KEY_SEPARATOR + target);
    }

    public List<GraphFeedback.Failure> facts(long now, double halfLifeTicks) {
        requireTick(now, "чтения памяти");
        requireHalfLife(halfLifeTicks);
        List<GraphFeedback.Failure> facts = new ArrayList<>(entries.size());
        Iterator<Entry> known = entries.values().iterator();
        while (known.hasNext()) {
            Entry entry = known.next();
            double weight = entry.weight(now, halfLifeTicks);
            if (weight < MIN_WEIGHT) {
                known.remove();
                continue;
            }
            facts.add(new GraphFeedback.Failure(
                    entry.node(), entry.target(), entry.count(), entry.cause(), entry.lastTick(), weight));
        }
        facts.sort((one, two) -> {
            int byTime = Long.compare(two.lastTick(), one.lastTick());
            return byTime != 0 ? byTime : one.node().compareTo(two.node());
        });
        return List.copyOf(facts);
    }

    public List<GraphFeedback.Failure> facts(long now) {
        return facts(now, DEFAULT_HALF_LIFE_TICKS);
    }

    public Optional<GraphFeedback.Failure> recent(String node, String target, long now, double halfLifeTicks) {
        return facts(now, halfLifeTicks).stream()
                .filter(failure ->
                        failure.node().equals(node) && failure.target().equals(target))
                .findFirst();
    }

    public Optional<GraphFeedback.Failure> recent(String node, String target, long now) {
        return recent(node, target, now, DEFAULT_HALF_LIFE_TICKS);
    }

    public Set<String> unreachablePoints(long now, double halfLifeTicks) {
        Set<String> points = new TreeSet<>();
        for (GraphFeedback.Failure failure : facts(now, halfLifeTicks)) {
            if (unreachable(failure.cause()) && !failure.target().isEmpty()) {
                points.add(failure.target());
            }
        }
        return Set.copyOf(points);
    }

    public Set<String> unreachablePoints(long now) {
        return unreachablePoints(now, DEFAULT_HALF_LIFE_TICKS);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static boolean unreachable(GraphFeedback.Cause cause) {
        return cause == GraphFeedback.Cause.NO_PATH || cause == GraphFeedback.Cause.NOT_REACHED;
    }

    private static List<Long> withTick(List<Long> ticks, long now) {
        List<Long> next = new ArrayList<>(ticks.size() + 1);
        next.addAll(ticks);
        next.add(now);
        if (next.size() > MAX_TICKS) {
            next.remove(0);
        }
        return List.copyOf(next);
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) {
            String oldestKey = null;
            long oldestTick = Long.MAX_VALUE;
            for (Map.Entry<String, Entry> entry : entries.entrySet()) {
                if (entry.getValue().lastTick() < oldestTick) {
                    oldestTick = entry.getValue().lastTick();
                    oldestKey = entry.getKey();
                }
            }
            entries.remove(oldestKey);
        }
    }

    private static void requireTick(long now, String what) {
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + what + " " + now + " отрицателен: время идёт вперёд");
        }
    }

    private static void requireHalfLife(double halfLifeTicks) {
        if (!Double.isFinite(halfLifeTicks) || halfLifeTicks <= 0) {
            throw new IllegalArgumentException("Полураспад памяти неудач " + halfLifeTicks
                    + " тиков: штраф затухает, поэтому полураспад положителен");
        }
    }

    public static @Nullable String node(String ticketId, @Nullable String cause) {
        String raw = cause != null && cause.startsWith(GraphTickets.PREFIX) ? cause : ticketId;
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
