package com.theminespirit.core.tactics.adapter;

public record ReasonFactor(String factor, double value, double weight) {
    public ReasonFactor {
        if (!(weight > 0)) {
            throw new IllegalArgumentException("Фактор " + factor + " с весом " + weight
                    + ": в записи решения только факторы, перевесившие соперника");
        }
    }
}
