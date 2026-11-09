package com.theminespirit.core.board.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class QuarryRanking {
    private static final Comparator<QuarryCandidate> ORDER = Comparator.comparingInt(QuarryCandidate::valuePermille)
            .reversed()
            .thenComparingInt(QuarryCandidate::pathLength)
            .thenComparingInt(candidate -> candidate.point().x())
            .thenComparingInt(candidate -> candidate.point().z())
            .thenComparing(candidate -> candidate.resource().name());

    private QuarryRanking() {}

    public static List<QuarryCandidate> best(List<QuarryCandidate> candidates, int limit) {
        Objects.requireNonNull(candidates, "Кандидаты не могут быть null: пустой поиск задают пустым списком");
        if (limit < 1) {
            throw new IllegalArgumentException("Предел списка " + limit + ": хотя бы один кандидат или не звать поиск");
        }
        List<QuarryCandidate> ordered = new ArrayList<>(
                candidates.stream().filter(candidate -> candidate.reserve() > 0).toList());
        ordered.sort(ORDER);
        return List.copyOf(ordered.subList(0, Math.min(limit, ordered.size())));
    }
}
