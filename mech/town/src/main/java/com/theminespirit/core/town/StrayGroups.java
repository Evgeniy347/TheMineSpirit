package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class StrayGroups {
    public record Cell(BlockCoord at, String blockId) {
        public Cell {
            Objects.requireNonNull(at, "Клетка лишнего блока не может быть null: по ней её сносят");
            Objects.requireNonNull(blockId, "Блок клетки не может быть null: по нему выбирают инструмент");
        }
    }

    private StrayGroups() {}
}
