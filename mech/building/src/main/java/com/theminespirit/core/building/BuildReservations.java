package com.theminespirit.core.building;

import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class BuildReservations {
    private final WorkReservations leases;
    private final LongSupplier clock;

    public BuildReservations() {
        this(new WorkReservations());
    }

    public BuildReservations(WorkReservations leases) {
        this(leases, () -> 0L);
    }

    public BuildReservations(WorkReservations leases, LongSupplier clock) {
        this.leases = Objects.requireNonNull(
                leases, "Общий реестр не может быть null: без него брони клеток не с кем сверять");
        this.clock = Objects.requireNonNull(clock, "Часы брони не могут быть null: срок брони считается по ним");
    }

    public boolean reserve(BlockCoord cell, UUID spirit) {
        return reserve(cell, spirit, clock.getAsLong());
    }

    public boolean reserve(BlockCoord cell, UUID spirit, long now) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что бронировать");
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чья бронь");
        return leases.reserve(target(cell), spirit, now, BuildTuning.RESERVATION_TICKS)
                != WorkReservations.Outcome.TAKEN_BY_OTHER;
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

    public int expire(long now) {
        return leases.expire(now);
    }

    public int size() {
        return leases.size();
    }

    private static WorkTarget target(BlockCoord cell) {
        return new WorkTarget(WorkKind.CELL, cell);
    }
}
