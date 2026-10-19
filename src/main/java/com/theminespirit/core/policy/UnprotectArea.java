package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record UnprotectArea(
        String id,
        String dimension,
        BlockBox box,
        String grantedBy,
        long grantedAtMillis,
        String reason,
        long expiresAtMillis) {
    public UnprotectArea {
        Objects.requireNonNull(id, "Id разрешённой области не может быть null: по нему снимают разрешение");
        Objects.requireNonNull(
                dimension, "Мир разрешённой области не может быть null: область принадлежит одному миру");
        Objects.requireNonNull(box, "Куб разрешённой области не может быть null: без него область ничего не разрешает");
        Objects.requireNonNull(grantedBy, "Автор разрешения не может быть null: по нему видно, кто попросил");
        Objects.requireNonNull(reason, "Причина разрешения не может быть null: по ней видно, зачем сняли защиту");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Id разрешённой области пуст: укажите непустой id");
        }
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Мир разрешённой области пуст: укажите, например minecraft:overworld");
        }
        if (grantedBy.isBlank()) {
            throw new IllegalArgumentException("Автор разрешения пуст: укажите, кто попросил снять защиту");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Причина разрешения пуста: укажите, зачем снимают защиту");
        }
        if (expiresAtMillis < 0) {
            throw new IllegalArgumentException("Срок жизни " + expiresAtMillis + " отрицателен: без срока задают 0");
        }
    }

    public boolean isActive(long nowMillis) {
        return expiresAtMillis == 0 || nowMillis < expiresAtMillis;
    }

    public boolean contains(String areaDimension, BlockCoord pos) {
        return dimension.equals(areaDimension) && box.contains(pos);
    }

    public long volume() {
        try {
            long dx = (long) box.max().x() - box.min().x() + 1;
            long dy = (long) box.max().y() - box.min().y() + 1;
            long dz = (long) box.max().z() - box.min().z() + 1;
            return Math.multiplyExact(Math.multiplyExact(dx, dy), dz);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
