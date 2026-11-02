package com.theminespirit.core.movement.network;

import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class TrailTrack {
    private static final int ONE_STEP = 1;

    private final PathNetwork network = new PathNetwork();
    private final Map<BlockCoord, Set<BlockCoord>> edges = new HashMap<>();
    private final Set<BlockCoord> walked = new HashSet<>();
    private int segments;

    public int record(List<BlockCoord> path) {
        Objects.requireNonNull(path, "Пройденный путь не может быть null: записывать нечего");
        int added = 0;
        for (List<BlockCoord> run : runs(path)) {
            for (int step = 1; step < run.size(); step++) {
                if (addEdge(run.get(step - 1), run.get(step))) {
                    added++;
                }
            }
            walked.addAll(run);
        }
        segments += added;
        return added;
    }

    public Optional<List<BlockCoord>> route(BlockCoord feet, BlockCoord target, SafetyView view) {
        Objects.requireNonNull(feet, "Позиция духа не может быть null: неизвестно, откуда идти по тропе");
        Objects.requireNonNull(target, "Цель не может быть null: неизвестно, куда идти по тропе");
        Objects.requireNonNull(view, "Мир не может быть null: по нему проверяется доступ к тропе");
        return NetworkRoute.plan(network, feet, target, view);
    }

    public boolean walked(BlockCoord cell) {
        return walked.contains(cell);
    }

    public int cells() {
        return walked.size();
    }

    public int segments() {
        return segments;
    }

    public PathNetwork network() {
        return network;
    }

    public void blockChanged(BlockCoord block) {
        network.blockChanged(block);
    }

    private boolean addEdge(BlockCoord from, BlockCoord to) {
        Set<BlockCoord> next = edges.computeIfAbsent(from, key -> new HashSet<>());
        if (!next.add(to)) {
            return false;
        }
        edges.computeIfAbsent(to, key -> new HashSet<>()).add(from);
        network.add(List.of(from, to));
        return true;
    }

    private static List<List<BlockCoord>> runs(List<BlockCoord> path) {
        List<List<BlockCoord>> runs = new ArrayList<>();
        List<BlockCoord> run = new ArrayList<>();
        for (BlockCoord cell : path) {
            Objects.requireNonNull(cell, "Клетка пути не может быть null: без координаты тропа рвётся");
            if (run.isEmpty() || adjacent(run.get(run.size() - 1), cell)) {
                if (run.isEmpty() || !run.get(run.size() - 1).equals(cell)) {
                    run.add(cell);
                }
                continue;
            }
            runs.add(List.copyOf(run));
            run.clear();
            run.add(cell);
        }
        if (!run.isEmpty()) {
            runs.add(List.copyOf(run));
        }
        return runs;
    }

    private static boolean adjacent(BlockCoord from, BlockCoord to) {
        return Math.abs(from.x() - to.x()) <= ONE_STEP
                && Math.abs(from.y() - to.y()) <= ONE_STEP
                && Math.abs(from.z() - to.z()) <= ONE_STEP;
    }
}
