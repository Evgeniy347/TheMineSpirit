package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class StuckSpirits {
    public static final int AREA_RADIUS = 4;
    private static final int MAX_CHANGES = 64;

    public record Stuck(@Nullable BlockCoord at, long since, boolean noPath) {
        public Stuck(@Nullable BlockCoord at, long since) {
            this(at, since, false);
        }

        public Stuck {
            if (since < 0) {
                throw new IllegalArgumentException(
                        "Тик застревания " + since + " отрицателен: отметка ставится только вперёд");
            }
        }
    }

    public record Change(UUID spirit, @Nullable BlockCoord at, long tick, boolean marked, String reason) {
        public Change {
            Objects.requireNonNull(spirit, "Дух изменения метки не может быть null: метка принадлежит духу");
            Objects.requireNonNull(reason, "Причина изменения метки не может быть null: без неё метку не объяснить");
        }
    }

    private final Map<UUID, BlockCoord> seen = new LinkedHashMap<>();
    private final Map<UUID, Stuck> stuck = new LinkedHashMap<>();
    private final List<Change> changes = new ArrayList<>();
    private boolean rebasePending;
    private long restoredAt;

    public void see(UUID spirit, BlockCoord at) {
        see(spirit, at, 0);
    }

    public void see(UUID spirit, BlockCoord at, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: позиция наблюдается у конкретного духа");
        Objects.requireNonNull(at, "Позиция не может быть null: по ней видно, сдвинулся ли дух");
        BlockCoord previous = seen.put(spirit, at);
        Stuck known = stuck.get(spirit);
        if (known == null) {
            return;
        }
        BlockCoord origin = known.at() == null ? previous : known.at();
        if (origin != null && !near(origin, at)) {
            stuck.remove(spirit);
            note(spirit, at, now, false, "сдвинулся с " + origin + " на " + at);
            return;
        }
        if (known.at() == null) {
            stuck.put(spirit, new Stuck(at, known.since(), known.noPath()));
        }
    }

    private static boolean near(BlockCoord origin, BlockCoord at) {
        return Math.abs(origin.x() - at.x()) <= AREA_RADIUS
                && Math.abs(origin.y() - at.y()) <= AREA_RADIUS
                && Math.abs(origin.z() - at.z()) <= AREA_RADIUS;
    }

    public void mark(UUID spirit, long now) {
        mark(spirit, now, "провал движения");
    }

    public void mark(UUID spirit, long now, String reason) {
        Objects.requireNonNull(spirit, "Дух не может быть null: застревает конкретный дух");
        Objects.requireNonNull(reason, "Причина метки не может быть null: без неё метку не объяснить");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: застревание не ставится в прошлое");
        }
        if (stuck.putIfAbsent(spirit, new Stuck(seen.get(spirit), now, StuckCause.noPath(reason))) == null) {
            note(spirit, seen.get(spirit), now, true, reason);
            return;
        }
        Stuck known = stuck.get(spirit);
        if (StuckCause.noPath(reason) && !known.noPath()) {
            stuck.put(spirit, new Stuck(known.at(), known.since(), true));
        }
    }

    public Optional<Stuck> of(UUID spirit, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: застревание читается у конкретного духа");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: застревание читается только вперёд");
        }
        rebase(now);
        Stuck known = stuck.get(spirit);
        if (known == null) {
            return Optional.empty();
        }
        if (now > known.since() && !known.noPath() && now - known.since() >= BoardTuning.STUCK_TTL_TICKS) {
            stuck.remove(spirit);
            note(
                    spirit,
                    known.at(),
                    now,
                    false,
                    "истекла: " + BoardTuning.STUCK_TTL_TICKS + " тиков без выхода из ловушки");
            return Optional.empty();
        }
        return Optional.of(known);
    }

    public boolean isStuck(UUID spirit, long now) {
        return of(spirit, now).isPresent();
    }

    public void release(UUID spirit) {
        release(spirit, 0, "снята вручную");
    }

    public void release(UUID spirit, long now, String reason) {
        Objects.requireNonNull(spirit, "Дух не может быть null: спасение снимает метку с конкретного духа");
        Objects.requireNonNull(reason, "Причина снятия не может быть null: без неё метку не объяснить");
        Stuck known = stuck.remove(spirit);
        if (known != null) {
            note(spirit, known.at(), now, false, reason);
        }
    }

    public List<Change> drainChanges() {
        List<Change> drained = List.copyOf(changes);
        changes.clear();
        return drained;
    }

    private void note(UUID spirit, @Nullable BlockCoord at, long tick, boolean marked, String reason) {
        if (changes.size() >= MAX_CHANGES) {
            changes.remove(0);
        }
        changes.add(new Change(spirit, at, Math.max(0, tick), marked, reason));
    }

    public List<BoardState.Stuck> snapshot() {
        List<BoardState.Stuck> states = new ArrayList<>();
        for (Map.Entry<UUID, Stuck> entry : stuck.entrySet()) {
            Stuck state = entry.getValue();
            states.add(new BoardState.Stuck(entry.getKey(), state.at(), state.since(), state.noPath()));
        }
        return List.copyOf(states);
    }

    public void restore(List<BoardState.Stuck> states, long now) {
        seen.clear();
        stuck.clear();
        for (BoardState.Stuck state : states) {
            stuck.put(state.spirit(), new Stuck(state.at(), state.since(), state.noPath()));
            if (state.at() != null) {
                seen.put(state.spirit(), state.at());
            }
        }
        rebasePending = !states.isEmpty();
        restoredAt = now;
    }

    private void rebase(long now) {
        if (!rebasePending || now <= restoredAt) {
            return;
        }
        rebasePending = false;
        for (Map.Entry<UUID, Stuck> entry : new LinkedHashMap<>(stuck).entrySet()) {
            Stuck state = entry.getValue();
            stuck.put(entry.getKey(), new Stuck(state.at(), now, state.noPath()));
        }
    }

    public void clear() {
        seen.clear();
        stuck.clear();
    }
}
