package com.theminespirit.neoforge.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class BoardBuildMeter {
    private long rebuildStarted;
    private long pruneStarted;
    private long rebuildMillis;
    private long pruneMillis;
    private final Map<String, Long> phaseStarted = new LinkedHashMap<>();
    private final Map<String, Long> phaseNanos = new LinkedHashMap<>();

    void rebuildStart() {
        rebuildStarted = System.nanoTime();
        phaseNanos.clear();
        phaseStarted.clear();
    }

    void pruneStart() {
        pruneStarted = System.nanoTime();
    }

    void pruneEnd() {
        pruneMillis = (System.nanoTime() - pruneStarted) / 1_000_000;
    }

    void rebuildEnd() {
        rebuildMillis = (System.nanoTime() - rebuildStarted) / 1_000_000;
    }

    public void reset() {
        rebuildMillis = 0;
        pruneMillis = 0;
        phaseNanos.clear();
        phaseStarted.clear();
    }

    public void phaseStart(String name) {
        phaseStarted.put(name, System.nanoTime());
    }

    public void phaseEnd(String name) {
        Long started = phaseStarted.remove(name);
        if (started != null) {
            phaseNanos.merge(name, System.nanoTime() - started, Long::sum);
        }
    }

    public void phaseAdd(String name, long millis) {
        phaseNanos.merge(name, millis * 1_000_000L, Long::sum);
    }

    public long rebuildMillis() {
        return rebuildMillis;
    }

    public long pruneMillis() {
        return pruneMillis;
    }

    public Map<String, Long> phaseMillis() {
        return phaseNanos.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() / 1_000_000,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public String breakdown() {
        Map<String, Long> phases = phaseMillis();
        phases.put("prune", pruneMillis);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Long> phase : phases.entrySet()) {
            if (phase.getValue() == 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(phase.getKey()).append('=').append(phase.getValue());
        }
        return text.length() == 0 ? "фаз нет" : text.toString();
    }
}
