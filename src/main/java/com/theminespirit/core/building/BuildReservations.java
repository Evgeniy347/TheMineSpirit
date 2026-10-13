package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BuildReservations {
    private final Map<BlockCoord, UUID> owners = new HashMap<>();

    public boolean reserve(BlockCoord cell, UUID spirit) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что бронировать");
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чья бронь");
        UUID current = owners.get(cell);
        if (current == null) {
            owners.put(cell, spirit);
            return true;
        }
        return current.equals(spirit);
    }

    public boolean heldBy(BlockCoord cell, UUID spirit) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, чью бронь проверять");
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чья бронь");
        return spirit.equals(owners.get(cell));
    }

    public boolean foreign(BlockCoord cell, UUID spirit) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, чья бронь мешает");
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чья бронь мешает");
        UUID current = owners.get(cell);
        return current != null && !current.equals(spirit);
    }

    public void release(BlockCoord cell, UUID spirit) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что освобождать");
        Objects.requireNonNull(spirit, "Дух не может быть null: чужую бронь снимать нельзя");
        owners.remove(cell, spirit);
    }

    public void releaseAll(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чьи брони снимать");
        owners.entrySet().removeIf(entry -> entry.getValue().equals(spirit));
    }

    public int size() {
        return owners.size();
    }
}
