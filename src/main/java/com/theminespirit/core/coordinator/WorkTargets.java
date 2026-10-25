package com.theminespirit.core.coordinator;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class WorkTargets {
    private static final Comparator<WorkTarget> ORDER =
            Comparator.comparing((WorkTarget target) -> target.kind()).thenComparing(WorkTarget::anchor);

    private WorkTargets() {}

    public static Optional<WorkTarget> current(Collection<WorkTarget> owned) {
        if (owned == null) {
            throw new IllegalArgumentException(
                    "owned не может быть null: нет броней — пустой набор, а не отсутствие набора");
        }
        for (WorkTarget target : owned) {
            if (target == null) {
                throw new IllegalArgumentException(
                        "Цель брони не может быть null: реестр броней держит только настоящие цели");
            }
        }
        return owned.stream().min(ORDER);
    }
}
