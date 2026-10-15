package com.theminespirit.core.doors;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.UUID;

public record SpiritRef(UUID id, BlockCoord position) {
    public SpiritRef {
        Objects.requireNonNull(id, "Дух без id не получить задачу: исполнитель адресуется UUID");
        Objects.requireNonNull(position, "Дух без позиции не выбирается: ближайший считается по расстоянию");
    }
}
