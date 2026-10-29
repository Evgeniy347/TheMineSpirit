package com.theminespirit.core.town;

import java.util.Objects;

public final class FloatingCleanup {
    public enum Action {
        REMOVE,
        DRAIN_CHEST,
        RELOCATE_STATION,
        SKIP_REGISTERED,
        SKIP_PROTECTED,
        SKIP_UNREACHABLE,
        SKIP_WAIT
    }

    public record Rooms(
            boolean yardReady, boolean playerBuilding, boolean chestEmpty, boolean stationNeeded, boolean reachable) {
        public Rooms(boolean yardReady, boolean playerBuilding, boolean chestEmpty, boolean stationNeeded) {
            this(yardReady, playerBuilding, chestEmpty, stationNeeded, true);
        }
    }

    private FloatingCleanup() {}

    public static Action decide(FloatingBlocks.Finding finding, Rooms rooms) {
        Objects.requireNonNull(finding, "Находка не может быть null: решение принимается по её виду");
        Objects.requireNonNull(rooms, "Обстановка не может быть null: без неё не ясно, есть ли куда убрать");
        if (rooms.playerBuilding()) {
            return Action.SKIP_PROTECTED;
        }
        return switch (finding.kind()) {
            case REGISTERED_CHEST -> Action.SKIP_REGISTERED;
            default -> {
                if (!rooms.reachable()) {
                    yield Action.SKIP_UNREACHABLE;
                }
                yield switch (finding.kind()) {
                    case CHEST -> {
                        if (!rooms.yardReady()) {
                            yield Action.SKIP_WAIT;
                        }
                        yield rooms.chestEmpty() ? Action.REMOVE : Action.DRAIN_CHEST;
                    }
                    case STATION -> {
                        if (!rooms.yardReady()) {
                            yield Action.SKIP_WAIT;
                        }
                        yield rooms.stationNeeded() ? Action.RELOCATE_STATION : Action.REMOVE;
                    }
                    default -> Action.REMOVE;
                };
            }
        };
    }
}
