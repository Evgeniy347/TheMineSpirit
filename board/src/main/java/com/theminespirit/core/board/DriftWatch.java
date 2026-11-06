package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DriftWatch {
    public static final int SAMPLES = 11;
    public static final double MIN_PATH = 32.0;
    public static final double MAX_NET = 8.0;
    public static final long WINDOW_TICKS = 6000;

    public record Drift(double path, double span, double net) {}

    private record Sample(BlockCoord cell, long tick) {}

    private final Map<UUID, Deque<Sample>> tracks = new HashMap<>();

    public Optional<Drift> observe(UUID spirit, BlockCoord at, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: колебание считается на конкретного духа");
        Objects.requireNonNull(at, "Клетка духа не может быть null: без неё не видно, колеблется ли он");
        Deque<Sample> track = tracks.computeIfAbsent(spirit, ignored -> new ArrayDeque<>());
        Sample last = track.peekLast();
        if (last != null && now - last.tick() > WINDOW_TICKS) {
            track.clear();
            last = null;
        }
        if (last == null || !last.cell().equals(at)) {
            track.addLast(new Sample(at, now));
        }
        while (track.size() > SAMPLES) {
            track.removeFirst();
        }
        if (track.size() < SAMPLES) {
            return Optional.empty();
        }
        Drift drift = measure(track);
        if (drift.path() >= MIN_PATH && drift.span() >= MIN_PATH && drift.net() <= MAX_NET) {
            tracks.remove(spirit);
            return Optional.of(drift);
        }
        return Optional.empty();
    }

    private static Drift measure(Deque<Sample> track) {
        Iterator<Sample> samples = track.iterator();
        BlockCoord first = samples.next().cell();
        BlockCoord previous = first;
        double path = 0.0;
        double span = 0.0;
        while (samples.hasNext()) {
            BlockCoord cell = samples.next().cell();
            path += horizontal(previous, cell);
            span = Math.max(span, horizontal(first, cell));
            previous = cell;
        }
        return new Drift(path, span, horizontal(first, previous));
    }

    private static double horizontal(BlockCoord from, BlockCoord to) {
        double dx = from.x() - to.x();
        double dz = from.z() - to.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
