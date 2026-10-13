package com.theminespirit.core.intellect;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record FortifyOrder(UUID builder, FortifyMode mode, List<BlockCoord> cells, List<UUID> guards) {
    public FortifyOrder {
        if (builder == null) {
            throw new IllegalArgumentException("Приказ укрепления без строителя не отдаётся: ставить некому");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Приказ укрепления без режима не отдаётся: строить не знают что");
        }
        if (cells == null) {
            throw new IllegalArgumentException(
                    "Приказ укрепления без клеток не отдаётся: пустой план — пустым списком");
        }
        if (guards == null) {
            throw new IllegalArgumentException(
                    "Приказ укрепления без списка защитников не отдаётся: пустое прикрытие — пустым списком");
        }
        if (guards.contains(builder)) {
            throw new IllegalArgumentException("Строитель в списке защитников: один дух не строит и не прикрывает сразу");
        }
        if (new HashSet<>(cells).size() != cells.size()) {
            throw new IllegalArgumentException("Дубли в клетках приказа: каждая клетка ставится один раз");
        }
        cells = List.copyOf(cells);
        guards = List.copyOf(guards);
    }
}
