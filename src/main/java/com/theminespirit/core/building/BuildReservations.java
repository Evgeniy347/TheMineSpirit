package com.theminespirit.core.building;

import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.UUID;

public final class BuildReservations {
    private static final long PERMANENT_LEASE = Long.MAX_VALUE;

    private final WorkReservations leases;

    public BuildReservations() {
        this(new WorkReservations());
    }

    public BuildReservations(WorkReservations leases) {
        this.leases = Objects.requireNonNull(leases,
                "Общий реестр не может быть null: без него брони клеток не с кем сверять");
    }

    public boolean reserve(BlockCoord cell, UUID spirit) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что бронировать");
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чья бронь");
        return leases.reserve(target(cell), spirit, 0, PERMANENT_LEASE) != WorkReservations.Outcome.TAKEN_BY_OTHER;
    }


    public boolean foreign(BlockCoord cell, UUID spirit) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, чья бронь мешает");
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чья бронь мешает");
        return leases.owner(target(cell)).map(owner -> !owner.equals(spirit)).orElse(false);
    }

    public void release(BlockCoord cell, UUID spirit) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что освобождать");
        Objects.requireNonNull(spirit, "Дух не может быть null: чужую бронь снимать нельзя");
        leases.release(target(cell), spirit);
    }

    public void releaseAll(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чьи брони снимать");
        leases.releaseAll(spirit);
    }

    public int size() {
        return leases.size();
    }

    private static WorkTarget target(BlockCoord cell) {
        return new WorkTarget(WorkKind.CELL, cell);
    }
}
