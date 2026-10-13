package com.theminespirit.core.intellect;

import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record FortifyRoster(UUID builder, List<UUID> guards, Optional<Point> site, FortifyMode mode) {
    public FortifyRoster {
        if (builder == null) {
            throw new IllegalArgumentException("Ростер укрепления без строителя не собирается: строить некому");
        }
        if (guards == null) {
            throw new IllegalArgumentException(
                    "Ростер укрепления без списка защитников не собирается: пустое прикрытие — пустым списком");
        }
        if (site == null) {
            throw new IllegalArgumentException(
                    "Ростер укрепления без контейнера точки не собирается: нет точки — Optional.empty()");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Ростер укрепления без режима не собирается: режим входит в приказ");
        }
        if (guards.contains(builder)) {
            throw new IllegalArgumentException("Строитель в списке защитников: один дух не строит и не прикрывает сразу");
        }
        guards = List.copyOf(guards);
    }
}
