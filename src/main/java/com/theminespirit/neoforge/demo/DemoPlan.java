package com.theminespirit.neoforge.demo;

import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Готовая к постановке демо-постройка: имя, схема, раскладка, содержимое сундука и блоки без предмета. */
record DemoPlan(String name, String scheme, DemoPlacement placement, Map<String, Integer> materials,
        List<Placement> preplaced) {
    record Placement(BlockCoord pos, String block) {
        Placement {
            Objects.requireNonNull(pos, "Позиция блока не может быть null: без неё блок не поставить");
            Objects.requireNonNull(block, "Id блока не может быть null: без него неизвестно, что ставить");
        }
    }

    DemoPlan {
        Objects.requireNonNull(name, "Название постройки не может быть null: его называет дух");
        Objects.requireNonNull(scheme, "Файл схемы не может быть null: без него нечего строить");
        Objects.requireNonNull(placement, "Раскладка не может быть null: без неё неизвестно, где строить");
        materials = Map.copyOf(materials);
        preplaced = List.copyOf(preplaced);
    }
}
