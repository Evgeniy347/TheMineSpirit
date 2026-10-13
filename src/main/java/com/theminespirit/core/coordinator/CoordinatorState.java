package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.Point;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CoordinatorState(Optional<Point> defendedPoint, double defendedRadius, List<SpiritSnapshot> spirits,
        long tick) {

    public CoordinatorState {
        if (defendedPoint == null) {
            throw new IllegalArgumentException(
                    "defendedPoint не может быть null: обороны нет — передайте Optional.empty()");
        }
        if (spirits == null) {
            throw new IllegalArgumentException(
                    "spirits не может быть null: пустое состояние — пустой список");
        }
        spirits = spirits.stream().sorted(Comparator.comparing(SpiritSnapshot::id)).toList();
        if (defendedPoint.isPresent() && defendedRadius <= 0) {
            throw new IllegalArgumentException("defendedRadius = " + defendedRadius
                    + " при защищаемой точке: радиус обороны обязан быть больше нуля, иначе сектора вырождены");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick = " + tick + ": тик состояния не может быть отрицательным");
        }
    }

    public List<SpiritSnapshot> defenders() {
        return spirits.stream().filter(spirit -> spirit.role() == SpiritRole.DEFENDER).toList();
    }

    public List<SpiritSnapshot> workers() {
        return spirits.stream().filter(spirit -> spirit.role() == SpiritRole.WORKER).toList();
    }

    public Optional<SpiritSnapshot> byId(UUID id) {
        return spirits.stream().filter(spirit -> spirit.id().equals(id)).findFirst();
    }
}
