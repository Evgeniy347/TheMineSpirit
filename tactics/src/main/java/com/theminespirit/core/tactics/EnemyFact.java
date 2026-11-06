package com.theminespirit.core.tactics;

public record EnemyFact(
        String type,
        String id,
        Point pos,
        double hpFraction,
        double armor,
        boolean visible,
        boolean targetingSelf,
        int count) {
    public static final int MAX_COUNT = 10_000;

    public EnemyFact {
        if (FactChecks.required("EnemyFact.type", type).isBlank()) {
            throw new IllegalArgumentException(
                    "EnemyFact.type пуст: тип врага называется id сущности, например minecraft:zombie");
        }
        FactChecks.required("EnemyFact.id", id);
        FactChecks.required("EnemyFact.pos", pos);
        FactChecks.range("EnemyFact.hpFraction", hpFraction, 0, 1);
        FactChecks.range("EnemyFact.armor", armor, 0, 20);
        FactChecks.range("EnemyFact.count", count, 1, MAX_COUNT);
    }

    public EnemyFact(
            String type,
            String id,
            Point pos,
            double hpFraction,
            double armor,
            boolean visible,
            boolean targetingSelf) {
        this(type, id, pos, hpFraction, armor, visible, targetingSelf, 1);
    }
}
