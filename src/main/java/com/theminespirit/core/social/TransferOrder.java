package com.theminespirit.core.social;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TransferOrder(Optional<UUID> from, UUID to, String item, int count, double distance) {
    public TransferOrder {
        Objects.requireNonNull(from, "Источник не может быть null: склад задают пустым Optional");
        Objects.requireNonNull(to, "Получатель не может быть null: передача адресная");
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Предмет передачи пуст: по нему ищут излишек и везут груз");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Передать предметов " + count + " <= 0: пустая передача "
                    + "не создаёт задачи");
        }
        if (Double.isNaN(distance) || Double.isInfinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Расстояние передачи " + distance + " обязано быть конечным и >= 0");
        }
    }

    public boolean fromWarehouse() {
        return from.isEmpty();
    }
}
