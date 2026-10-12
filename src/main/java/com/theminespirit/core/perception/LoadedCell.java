package com.theminespirit.core.perception;

import java.util.List;
import java.util.Objects;

public record LoadedCell(String blockId, boolean container, List<String> entityTypes) {
    public LoadedCell {
        Objects.requireNonNull(blockId, "blockId не может быть null: клетка загруженного чанка всегда содержит блок");
        Objects.requireNonNull(entityTypes, "entityTypes не может быть null: пустая клетка — пустой список");
        for (String type : entityTypes) {
            Objects.requireNonNull(type, "Список entityTypes содержит null: сущность обязана назвать свой тип");
        }
        entityTypes = List.copyOf(entityTypes);
    }
}
