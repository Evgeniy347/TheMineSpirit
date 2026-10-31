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
        SKIP_PLANNED,
        SKIP_WAIT
    }

    public record Rooms(
            boolean yardReady,
            boolean playerBuilding,
            boolean chestEmpty,
            boolean stationNeeded,
            boolean reachable,
            boolean plannedFence) {
        public Rooms(boolean yardReady, boolean playerBuilding, boolean chestEmpty, boolean stationNeeded) {
            this(yardReady, playerBuilding, chestEmpty, stationNeeded, true, false);
        }

        public Rooms(
                boolean yardReady,
                boolean playerBuilding,
                boolean chestEmpty,
                boolean stationNeeded,
                boolean reachable) {
            this(yardReady, playerBuilding, chestEmpty, stationNeeded, reachable, false);
        }
    }

    private FloatingCleanup() {}

    private static boolean isFence(FloatingBlocks.Finding finding) {
        return finding.kind() == FloatingBlocks.Kind.FENCE;
    }

    public static Action decide(FloatingBlocks.Finding finding, Rooms rooms) {
        Objects.requireNonNull(finding, "Находка не может быть null: решение принимается по её виду");
        Objects.requireNonNull(rooms, "Обстановка не может быть null: без неё не ясно, есть ли куда убрать");
        if (finding.kind() == FloatingBlocks.Kind.TREE) {
            return rooms.reachable() ? Action.REMOVE : Action.SKIP_UNREACHABLE;
        }
        if (rooms.playerBuilding()) {
            return Action.SKIP_PROTECTED;
        }
        if (rooms.plannedFence() && isFence(finding)) {
            return Action.SKIP_PLANNED;
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
