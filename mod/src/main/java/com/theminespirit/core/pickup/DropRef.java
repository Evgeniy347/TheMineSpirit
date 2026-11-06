package com.theminespirit.core.pickup;

import java.util.Objects;

public record DropRef(String id, String item, int count, double x, double y, double z, boolean pickable) {
    public DropRef {
        Objects.requireNonNull(id, "id не может быть null: по id кучку отличают в списке пропущенных");
        Objects.requireNonNull(item, "item не может быть null: фильтр и сообщение об ошибке называют предмет");
    }
}
