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

    public record Stuck(@Nullable BlockCoord at, long since) {
        public Stuck {
            if (since < 0) {
                throw new IllegalArgumentException(
                        "Тик застревания " + since + " отрицателен: отметка ставится только вперёд");
            }
        }
    }

    private final Map<UUID, BlockCoord> seen = new LinkedHashMap<>();
    private final Map<UUID, Stuck> stuck = new LinkedHashMap<>();

    public void see(UUID spirit, BlockCoord at) {
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
            return;
        }
        if (known.at() == null) {
            stuck.put(spirit, new Stuck(at, known.since()));
        }
    }

    private static boolean near(BlockCoord origin, BlockCoord at) {
        return Math.abs(origin.x() - at.x()) <= AREA_RADIUS
                && Math.abs(origin.y() - at.y()) <= AREA_RADIUS
                && Math.abs(origin.z() - at.z()) <= AREA_RADIUS;
    }

    public void mark(UUID spirit, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: застревает конкретный дух");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: застревание не ставится в прошлое");
        }
        stuck.putIfAbsent(spirit, new Stuck(seen.get(spirit), now));
    }

    public Optional<Stuck> of(UUID spirit, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: застревание читается у конкретного духа");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: застревание читается только вперёд");
        }
        Stuck known = stuck.get(spirit);
        if (known == null) {
            return Optional.empty();
        }
        return Optional.of(known);
    }

    public boolean isStuck(UUID spirit, long now) {
        return of(spirit, now).isPresent();
    }

    public void release(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: спасение снимает метку с конкретного духа");
        stuck.remove(spirit);
    }

    public List<BoardState.Stuck> snapshot() {
        List<BoardState.Stuck> states = new ArrayList<>();
        for (Map.Entry<UUID, Stuck> entry : stuck.entrySet()) {
            Stuck state = entry.getValue();
            states.add(new BoardState.Stuck(entry.getKey(), state.at(), state.since()));
        }
        return List.copyOf(states);
    }

    public void restore(List<BoardState.Stuck> states, long now) {
        seen.clear();
        stuck.clear();
        for (BoardState.Stuck state : states) {
            stuck.put(state.spirit(), new Stuck(state.at(), Math.max(now, state.since())));
            if (state.at() != null) {
                seen.put(state.spirit(), state.at());
            }
        }
    }

    public void clear() {
        seen.clear();
        stuck.clear();
    }
}
