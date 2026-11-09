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
    private static final int MAX_CHANGES = 64;
    private static final long RESCUE_RELAPSE_TICKS = 12_000;
    private static final int RESCUE_RELAPSE_RADIUS = 8;

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

    public record Rescue(@Nullable BlockCoord at, long tick, int count) {
        public Rescue {
            if (tick < 0) {
                throw new IllegalArgumentException("Тик спасения " + tick + " отрицателен: время идёт вперёд");
            }
            if (count < 1) {
                throw new IllegalArgumentException("Число спасений " + count + " меньше одного: запись о спасении");
            }
        }
    }

    private final Map<UUID, BlockCoord> seen = new LinkedHashMap<>();
    private final Map<UUID, Stuck> stuck = new LinkedHashMap<>();
    private final Map<UUID, Rescue> rescues = new LinkedHashMap<>();
    private final List<Change> changes = new ArrayList<>();

    public void see(UUID spirit, BlockCoord at) {
        see(spirit, at, 0, false);
    }

    public void see(UUID spirit, BlockCoord at, long now) {
        see(spirit, at, now, false);
    }

    public void see(UUID spirit, BlockCoord at, long now, boolean escaped) {
        Objects.requireNonNull(spirit, "Дух не может быть null: позиция наблюдается у конкретного духа");
        Objects.requireNonNull(at, "Позиция не может быть null: по ней видно, сдвинулся ли дух");
        seen.put(spirit, at);
        forgetDistantRescue(spirit, at);
        Stuck known = stuck.get(spirit);
        if (known == null) {
            return;
        }
        if (escaped) {
            stuck.remove(spirit);
            note(spirit, at, now, false, "вышел на поверхность: с " + at + " (метка с тика " + known.since() + ")");
            return;
        }
        if (known.at() == null || !known.at().equals(at)) {
            stuck.put(spirit, new Stuck(at, known.since(), known.noPath()));
            return;
        }
    }

    public void mark(UUID spirit, long now) {
        mark(spirit, now, "провал движения");
    }

    public void mark(UUID spirit, long now, String reason) {
        mark(spirit, now, reason, false);
    }

    public void mark(UUID spirit, long now, String reason, boolean noPath) {
        Objects.requireNonNull(spirit, "Дух не может быть null: застревает конкретный дух");
        Objects.requireNonNull(reason, "Причина метки не может быть null: без неё метку не объяснить");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: застревание не ставится в прошлое");
        }
        if (stuck.putIfAbsent(spirit, new Stuck(seen.get(spirit), now, noPath)) == null) {
            note(spirit, seen.get(spirit), now, true, reason);
            return;
        }
        Stuck known = stuck.get(spirit);
        if (noPath && !known.noPath()) {
            stuck.put(spirit, new Stuck(known.at(), known.since(), true));
        }
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
        if (now > known.since() && now - known.since() >= BoardTuning.STUCK_TTL_TICKS) {
            stuck.remove(spirit);
            note(
                    spirit,
                    known.at(),
                    now,
                    false,
                    "истекла: " + BoardTuning.STUCK_TTL_TICKS + " тиков без выхода из ловушки"
                            + (known.noPath() ? " (в том числе «пути нет»: метка не вечная)" : ""));
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

    public void rescued(UUID spirit, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: спасение адресное");
        Stuck known = stuck.get(spirit);
        Rescue previous = rescues.get(spirit);
        rescues.put(
                spirit,
                new Rescue(known == null ? null : known.at(), now, previous == null ? 1 : previous.count() + 1));
    }

    public boolean relapsed(UUID spirit, @Nullable BlockCoord at, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: память спасений адресная");
        Rescue known = rescues.get(spirit);
        if (known == null || now < known.tick() || now - known.tick() > RESCUE_RELAPSE_TICKS) {
            return false;
        }
        if (known.at() == null || at == null) {
            return true;
        }
        int dx = Math.abs(known.at().x() - at.x());
        int dy = Math.abs(known.at().y() - at.y());
        int dz = Math.abs(known.at().z() - at.z());
        return dx <= RESCUE_RELAPSE_RADIUS && dy <= RESCUE_RELAPSE_RADIUS && dz <= RESCUE_RELAPSE_RADIUS;
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
        if (now < 0) {
            throw new IllegalArgumentException("Тик загрузки " + now + " отрицателен: метки читаются только вперёд");
        }
        seen.clear();
        stuck.clear();
        rescues.clear();
        for (BoardState.Stuck state : states) {
            boolean stale = state.since() > now || now - state.since() >= BoardTuning.STUCK_TTL_TICKS;
            if (stale) {
                note(
                        state.spirit(),
                        state.at(),
                        now,
                        false,
                        "истекла до загрузки: метка с тика " + state.since() + " старше " + BoardTuning.STUCK_TTL_TICKS
                                + " тиков");
                continue;
            }
            stuck.put(state.spirit(), new Stuck(state.at(), state.since(), state.noPath()));
            if (state.at() != null) {
                seen.put(state.spirit(), state.at());
            }
        }
    }

    public void clear() {
        seen.clear();
        stuck.clear();
        rescues.clear();
    }

    private void forgetDistantRescue(UUID spirit, BlockCoord at) {
        Rescue known = rescues.get(spirit);
        if (known == null || known.at() == null) {
            return;
        }
        int dx = Math.abs(known.at().x() - at.x());
        int dy = Math.abs(known.at().y() - at.y());
        int dz = Math.abs(known.at().z() - at.z());
        if (dx > RESCUE_RELAPSE_RADIUS * 2 || dy > RESCUE_RELAPSE_RADIUS * 2 || dz > RESCUE_RELAPSE_RADIUS * 2) {
            rescues.remove(spirit);
        }
    }
}
