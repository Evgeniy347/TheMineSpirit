package com.theminespirit.core.rest;

public record SettlementLevelView(int level, int y0, int x, int z, String rule) {
    public SettlementLevelView {
        if (y0 != level - 1) {
            throw new IllegalArgumentException(
                    "Уровень ног " + level + " и верх грунта " + y0 + ": y0 обязан быть на 1 ниже");
        }
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("Правило уровня пусто: по нему видно, чем выбрана высота");
        }
    }
}
