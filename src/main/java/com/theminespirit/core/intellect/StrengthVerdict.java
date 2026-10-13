package com.theminespirit.core.intellect;

import java.util.Optional;
import java.util.UUID;

public record StrengthVerdict(UUID spirit, StrengthAction action, double winMargin, Optional<UUID> enemy) {
    public StrengthVerdict {
        if (spirit == null) {
            throw new IllegalArgumentException("Вердикт без духа не применяется: бой ведут конкретные духи");
        }
        if (action == null) {
            throw new IllegalArgumentException("Вердикт без действия не применяется: действие входит в приказ духу");
        }
        if (Double.isNaN(winMargin) || Double.isInfinite(winMargin) || winMargin < -1 || winMargin > 1) {
            throw new IllegalArgumentException(
                    "Запас победы = " + winMargin + ": обязан лежать в -1..1, как WinMargin тактики");
        }
        if (enemy == null) {
            throw new IllegalArgumentException(
                    "Вердикт без контейнера врага не применяется: отсутствие цели задают через Optional.empty()");
        }
    }
}
