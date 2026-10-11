package com.theminespirit.core.tactics;

public record ArmorModel(double perPoint, double cap) {
    public ArmorModel {
        FactChecks.range("ArmorModel.perPoint", perPoint, 0, 1);
        FactChecks.range("ArmorModel.cap", cap, 0, 0.95);
    }

    public double effectiveHp(double hp, double armor) {
        return hp / (1 - Math.min(cap, perPoint * armor));
    }
}
