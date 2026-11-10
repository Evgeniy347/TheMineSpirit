package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SurroundingTargets {
    private static final int CANDIDATE_LIMIT = 32;
    private static final int TARGET_LIMIT = 4;
    private static final int TARGET_SEPARATION = 8;

    record Near(BlockCoord at, int distance) {}

    private SurroundingTargets() {}

    static void offer(Map<String, List<Near>> candidates, String tag, BlockCoord at, int distance) {
        List<Near> known = candidates.computeIfAbsent(tag, key -> new ArrayList<>());
        for (Near before : known) {
            if (before.at().equals(at)) {
                return;
            }
        }
        int index = 0;
        while (index < known.size() && known.get(index).distance() <= distance) {
            index++;
        }
        known.add(index, new Near(at, distance));
        if (known.size() > CANDIDATE_LIMIT) {
            known.remove(known.size() - 1);
        }
    }

    static Map<String, List<BlockCoord>> of(Map<String, List<Near>> candidates) {
        Map<String, List<BlockCoord>> targets = new HashMap<>();
        candidates.forEach((tag, known) -> {
            List<BlockCoord> chosen = new ArrayList<>();
            for (Near candidate : known) {
                boolean distinct = chosen.stream()
                        .allMatch(at -> distanceSquared(at, candidate.at()) >= TARGET_SEPARATION * TARGET_SEPARATION);
                if (distinct) {
                    chosen.add(candidate.at());
                }
                if (chosen.size() >= TARGET_LIMIT) {
                    break;
                }
            }
            if (!chosen.isEmpty()) {
                targets.put(tag, List.copyOf(chosen));
            }
        });
        return targets;
    }

    private static int distanceSquared(BlockCoord one, BlockCoord two) {
        int dx = one.x() - two.x();
        int dy = one.y() - two.y();
        int dz = one.z() - two.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
