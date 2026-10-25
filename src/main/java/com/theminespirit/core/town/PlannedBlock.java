package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record PlannedBlock(BlockCoord at, String block) {
    public PlannedBlock {
        Objects.requireNonNull(at, "Клетка блока плана не может быть null");
        Objects.requireNonNull(block, "Блок плана не может быть null: ставится конкретный блок");
    }
}
