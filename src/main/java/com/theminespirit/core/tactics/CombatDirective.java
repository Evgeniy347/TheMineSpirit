package com.theminespirit.core.tactics;

import java.util.Objects;
import java.util.Optional;

public record CombatDirective(CombatMode mode, Optional<TargetSelector.Target> target,
        Optional<RetreatMethod> retreat, Optional<Point> safePoint) {
    public CombatDirective {
        Objects.requireNonNull(mode, "mode не может быть null: режим задаётся приказом явно");
        Objects.requireNonNull(target, "target не может быть null: отсутствие цели — Optional.empty()");
        Objects.requireNonNull(retreat, "retreat не может быть null: отсутствие способа — Optional.empty()");
        Objects.requireNonNull(safePoint, "safePoint не может быть null: отсутствие точки — Optional.empty()");
        boolean combatMode = mode == CombatMode.AGGRESSIVE || mode == CombatMode.PROTECT;
        if (combatMode && target.isEmpty()) {
            throw new IllegalArgumentException("Режим " + mode + " без цели: боевой приказ обязан назвать, "
                    + "кого бить или кого защищать, иначе дух встанет и будет ждать неизвестно чего");
        }
        if (target.isPresent() && !combatMode) {
            throw new IllegalArgumentException("Цель " + target.get() + " при режиме " + mode
                    + ": цель задаётся только боя — иначе дух бьёт не того, кого велено");
        }
        if (safePoint.isPresent() && (retreat.isEmpty() || retreat.get() == RetreatMethod.NONE)) {
            throw new IllegalArgumentException("Точка отхода " + safePoint.get() + " без способа отхода: "
                    + "точка имеет смысл только вместе с явным способом, иначе дух не знает, зачем она нужна");
        }
    }

    public static CombatDirective attack(TargetSelector.Target target) {
        Objects.requireNonNull(target, "target не может быть null: атака обязана назвать цель");
        return new CombatDirective(CombatMode.AGGRESSIVE, Optional.of(target), Optional.empty(), Optional.empty());
    }

    public static CombatDirective defend(TargetSelector.Target target) {
        Objects.requireNonNull(target, "target не может быть null: защита обязана назвать подопечного");
        return new CombatDirective(CombatMode.PROTECT, Optional.of(target), Optional.empty(), Optional.empty());
    }

    public static CombatDirective flee() {
        return new CombatDirective(CombatMode.PASSIVE, Optional.empty(), Optional.of(RetreatMethod.NONE),
                Optional.empty());
    }

    public static CombatDirective retreat(Point point) {
        Objects.requireNonNull(point, "point не может быть null: отход обязан идти к конкретной точке");
        return new CombatDirective(CombatMode.PASSIVE, Optional.empty(), Optional.of(RetreatMethod.HOME),
                Optional.of(point));
    }
}
