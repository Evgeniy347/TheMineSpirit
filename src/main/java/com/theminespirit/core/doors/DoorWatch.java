package com.theminespirit.core.doors;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class DoorWatch {
    private final DoorTuning.DoorRules rules;

    public DoorWatch(DoorTuning.DoorRules rules) {
        this.rules = Objects.requireNonNull(
                rules, "Решение без правил не принимается: числа живут в doors.json, код их не дублирует");
    }

    public static DoorWatch withDefaultRules() {
        return new DoorWatch(DoorTuning.loadDefault());
    }

    public List<DoorClosing> decide(List<DoorState> doors, List<SpiritRef> spirits, long nowTick, boolean evening) {
        if (doors == null) {
            throw new IllegalArgumentException("Список дверей не может быть null: нет дверей — пустой список");
        }
        if (spirits == null) {
            throw new IllegalArgumentException("Список духов не может быть null: нет духов — пустой список");
        }
        if (nowTick < 0) {
            throw new IllegalArgumentException("nowTick = " + nowTick + ": время не может быть отрицательным");
        }
        List<DoorState> candidates = doors.stream()
                .filter(door -> door.open())
                .filter(door -> !playerNearby(door))
                .filter(door -> evening || nowTick - door.openedSinceTick() >= rules.closeAfterOpenTicks())
                .sorted(Comparator.comparing(DoorState::position))
                .toList();
        Set<UUID> busy = new HashSet<>();
        List<DoorClosing> closings = new ArrayList<>();
        for (DoorState door : candidates) {
            SpiritRef nearest = nearestFreeSpirit(door.position(), spirits, busy);
            if (nearest == null) {
                continue;
            }
            busy.add(nearest.id());
            closings.add(new DoorClosing(door.position(), nearest.id()));
        }
        return List.copyOf(closings);
    }

    public boolean isEvening(double dayFraction) {
        if (Double.isNaN(dayFraction) || dayFraction < 0.0 || dayFraction > 1.0) {
            throw new IllegalArgumentException("Доля суток = " + dayFraction + ": время суток обязано лежать в 0..1");
        }
        return dayFraction >= rules.eveningDayFraction();
    }

    public boolean inSettlement(BlockCoord door, List<BlockCoord> homes) {
        if (door == null) {
            throw new IllegalArgumentException("Дверь без позиции не проверить: принадлежность считается от координат");
        }
        if (homes == null) {
            throw new IllegalArgumentException("Список домов не может быть null: нет домов — пустой список");
        }
        for (BlockCoord home : homes) {
            if (distance(door, home) <= rules.settlementRadius()) {
                return true;
            }
        }
        return false;
    }

    private boolean playerNearby(DoorState door) {
        for (BlockCoord player : door.players()) {
            if (distance(door.position(), player) <= rules.playerNearbyRadius()) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable SpiritRef nearestFreeSpirit(BlockCoord door, List<SpiritRef> spirits, Set<UUID> busy) {
        SpiritRef best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (SpiritRef spirit : spirits) {
            if (busy.contains(spirit.id())) {
                continue;
            }
            double distance = distance(door, spirit.position());
            if (distance < bestDistance
                    || (distance == bestDistance && best != null && spirit.id().compareTo(best.id()) < 0)) {
                best = spirit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double distance(BlockCoord first, BlockCoord second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
