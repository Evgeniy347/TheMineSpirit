package com.theminespirit.core.tactics;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record RetreatOptions(List<RetreatCandidate> candidates) {
    public static final double TIE_EPSILON = 1e-9;

    public RetreatOptions {
        candidates = List.copyOf(candidates);
    }

    public int routes() {
        Set<RetreatMethod> methods = EnumSet.noneOf(RetreatMethod.class);
        candidates.forEach(candidate -> methods.add(candidate.entry().method()));
        return methods.size();
    }

    public double bestUtility() {
        double best = 0;
        for (RetreatCandidate candidate : candidates) {
            best = Math.max(best, candidate.utility());
        }
        return best;
    }

    public RetreatChoice choice() {
        RetreatCandidate best = null;
        RetreatCandidate rally = null;
        List<RetreatCandidate> ordered = candidates.stream()
                .sorted(Comparator.comparing(candidate -> candidate.entry().method())).toList();
        for (RetreatCandidate candidate : ordered) {
            if (candidate.entry().method() == RetreatMethod.RALLY) {
                if (rally == null || candidate.utility() > rally.utility() + TIE_EPSILON) {
                    rally = candidate;
                }
            } else if (best == null || candidate.utility() > best.utility() + TIE_EPSILON) {
                best = candidate;
            }
        }
        RetreatCandidate chosen = rally != null ? rally : best;
        return chosen == null ? RetreatChoice.NONE
                : new RetreatChoice(chosen.entry().method(), Optional.of(chosen.entry().id()));
    }
}
