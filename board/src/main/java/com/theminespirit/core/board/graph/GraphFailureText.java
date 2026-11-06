package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

public final class GraphFailureText {
    public static final int SHOWN = 6;

    private GraphFailureText() {}

    public static String line(List<GraphFeedback.Failure> failures) {
        Objects.requireNonNull(failures, "Память неудач не может быть null: без провалов — пустой список");
        if (failures.isEmpty()) {
            return "";
        }
        List<GraphFeedback.Failure> sorted = new ArrayList<>(failures);
        sorted.sort(Comparator.comparingDouble(GraphFeedback.Failure::weight)
                .reversed()
                .thenComparing(GraphFeedback.Failure::node)
                .thenComparing(GraphFeedback.Failure::target));
        StringJoiner text = new StringJoiner("; ");
        int shown = Math.min(SHOWN, sorted.size());
        for (int index = 0; index < shown; index++) {
            GraphFeedback.Failure failure = sorted.get(index);
            text.add(failure.node() + " → " + (failure.target().isBlank() ? "-" : failure.target()) + ": "
                    + failure.cause() + ", штраф " + weight(failure.weight()) + ", провалов "
                    + failure.count());
        }
        if (sorted.size() > shown) {
            text.add("… ещё " + (sorted.size() - shown));
        }
        return text.toString();
    }

    private static String weight(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
