package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkAllocator;
import com.theminespirit.core.coordinator.WorkAssignment;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

public final class WorkReserveHub {
    private static final Map<Level, WorkReservations> TABLES = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private WorkReserveHub() {}

    public static WorkReservations leasesFor(Level level) {
        synchronized (LOCK) {
            if (level == null) {
                throw new IllegalArgumentException("level не может быть null: реестр броней привязан к уровню");
            }
            return TABLES.computeIfAbsent(level, ignored -> new WorkReservations());
        }
    }

    public static boolean reserve(SpiritEntity spirit, WorkTarget target) {
        requireSpirit(spirit);
        long now = spirit.level().getGameTime();
        return leasesFor(spirit.level()).reserve(target, spirit.getUUID(), now, CoordinatorTuning.WORK_LEASE_TICKS)
                != WorkReservations.Outcome.TAKEN_BY_OTHER;
    }

    public static boolean release(SpiritEntity spirit, WorkTarget target) {
        requireSpirit(spirit);
        return leasesFor(spirit.level()).release(target, spirit.getUUID());
    }

    public static int releaseAll(SpiritEntity spirit) {
        requireSpirit(spirit);
        return leasesFor(spirit.level()).releaseAll(spirit.getUUID());
    }

    public static Optional<WorkTarget> choose(SpiritEntity spirit, List<WorkTarget> candidates) {
        requireSpirit(spirit);
        if (candidates == null) {
            throw new IllegalArgumentException("candidates не может быть null: нет целей — пустой список");
        }
        long now = spirit.level().getGameTime();
        SpiritSnapshot self = SpiritSnapshotReader.read(spirit, now);
        List<WorkAssignment> assignments =
                new WorkAllocator().allocate(List.of(self), candidates, leasesFor(spirit.level()), now);
        return assignments.isEmpty()
                ? Optional.empty()
                : Optional.of(assignments.get(0).target());
    }

    private static void requireSpirit(SpiritEntity spirit) {
        if (spirit == null) {
            throw new IllegalArgumentException("spirit не может быть null: без духа бронь некому выдать");
        }
    }
}
