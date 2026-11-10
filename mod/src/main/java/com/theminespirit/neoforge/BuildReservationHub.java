package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.Objects;
import net.minecraft.world.level.Level;

public final class BuildReservationHub {
    private BuildReservationHub() {}

    public static BuildReservations forLevel(Level level) {
        Objects.requireNonNull(level, "Уровень не может быть null: без него неизвестно, чей реестр брони");
        return new BuildReservations(WorkReserveHub.leasesFor(level), level::getGameTime);
    }
}
