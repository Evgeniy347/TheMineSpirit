package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class ZonePolicy {
    public static final int ZONE_MARGIN = 3;

    private ZonePolicy() {}

    public sealed interface TargetCheck {}

    public record Allowed() implements TargetCheck {}

    public record Refused(String reason) implements TargetCheck {
        public Refused {
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней строится итог задачи");
        }
    }

    public static TargetCheck checkTarget(BlockCoord pos, WorkZone zone) {
        Objects.requireNonNull(pos, "Цель не может быть null: проверяется, лежит ли она в рабочей зоне");
        Objects.requireNonNull(zone, "Зона не может быть null: без зоны проверку не вызывают");
        int excess = zone.excess(pos);
        if (excess == 0) {
            return new Allowed();
        }
        return new Refused("вне рабочей зоны: цель " + pos.x() + " " + pos.y() + " " + pos.z() + " на " + excess
                + " блоков за границей зоны (центр " + zone.center().x() + " "
                + zone.center().y() + " " + zone.center().z()
                + ", радиус " + zone.radius() + ")");
    }

    public static PolicyVerdict evaluateInside(BlockCoord pos, WorkZone zone) {
        Objects.requireNonNull(pos, "Позиция духа не может быть null: проверяется, не вышел ли дух из зоны");
        Objects.requireNonNull(zone, "Зона не может быть null: без зоны проверку не вызывают");
        int excess = zone.excess(pos);
        if (excess <= ZONE_MARGIN) {
            return new PolicyVerdict.Clear();
        }
        return new PolicyVerdict.Reflex(
                PolicyId.WORK_ZONE,
                new ReflexAction.ReturnToZone(zone.nearestInside(pos)),
                "дух вышел на " + excess + " блоков за рабочую зону (допуск " + ZONE_MARGIN + ")");
    }
}
