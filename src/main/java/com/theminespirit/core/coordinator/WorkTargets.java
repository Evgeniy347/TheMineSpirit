package com.theminespirit.core.coordinator;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Текущая цель работы духа из набора его броней — то, чем дух занят прямо сейчас.
 * Снимок духа несёт одну цель, а броней у него может быть несколько (клетка стройки, дерево, жила):
 * берётся меньшая по виду и якорю, чтобы выбор не зависел от порядка в реестре.
 */
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
