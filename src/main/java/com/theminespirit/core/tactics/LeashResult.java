package com.theminespirit.core.tactics;

public sealed interface LeashResult permits LeashResult.Within, LeashResult.Exceeded {
    double left();

    record Within(double left) implements LeashResult {
    }

    record Exceeded(double excess) implements LeashResult {
        @Override
        public double left() {
            return -excess;
        }
    }
}
