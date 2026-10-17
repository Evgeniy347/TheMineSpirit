package com.theminespirit.core.social;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.UUID;

public record TransferNeed(UUID recipient, String item, int count, BlockCoord at) {
    public TransferNeed {
        Objects.requireNonNull(recipient, "Получатель не может быть null: передача адресная");
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Предмет передачи пуст: без имени нечего искать у источника");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Нужно предметов " + count + " <= 0: передача без нужды "
                    + "не планируется");
        }
        Objects.requireNonNull(at, "Точка получателя не может быть null: по ней измеряется близость источника");
    }
}
