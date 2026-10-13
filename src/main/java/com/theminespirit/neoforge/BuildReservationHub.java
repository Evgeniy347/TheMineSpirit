package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

public final class BuildReservationHub {
    private static final Map<Level, BuildReservations> TABLES = new WeakHashMap<>();

    private BuildReservationHub() {
    }

    public static synchronized BuildReservations forLevel(Level level) {
        Objects.requireNonNull(level, "Уровень не может быть null: без него неизвестно, чей реестр брони");
        return TABLES.computeIfAbsent(level, ignored -> new BuildReservations());
    }
}
