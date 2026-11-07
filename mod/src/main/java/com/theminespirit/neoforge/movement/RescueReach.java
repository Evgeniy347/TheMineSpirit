package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.DescentPath;
import com.theminespirit.core.navigation.NavAStarSession;
import com.theminespirit.core.navigation.NavContext;
import com.theminespirit.core.navigation.NavFavoring;
import com.theminespirit.core.navigation.NavGoalNear;
import com.theminespirit.core.navigation.NavPath;
import com.theminespirit.core.navigation.NavResult;
import com.theminespirit.core.navigation.NavSettings;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class RescueReach {
    private static final int RADIUS = 2;
    private static final long VERDICT_TTL_TICKS = 1200;
    private static final long PROBE_TTL_TICKS = 1200;
    private static final int MAX_VERDICTS = 64;
    private static final int MAX_PENDING = 8;
    private static final NavSettings WALK_ONLY =
            new NavSettings(600_000, 600_000, 30_000, 50, 3, 12, 2, 20, 2, false, false);

    public record Verdict(boolean reachable, String why) {}

    private record Key(UUID helper, BlockCoord from, BlockCoord target) {}

    private record Entry(Verdict verdict, long at) {}

    private static final class Probe {
        private final Key key;
        private final NavAStarSession session;
        private final Predicate<BlockCoord> water;
        private final long started;

        private Probe(Key key, NavWorld world, Predicate<BlockCoord> water, long now) {
            this.key = key;
            this.water = water;
            this.started = now;
            this.session = new NavAStarSession(
                    new NavContext(world, WALK_ONLY, 0),
                    key.from().x(),
                    key.from().y(),
                    key.from().z(),
                    new NavGoalNear(
                            key.target().x(), key.target().y(), key.target().z(), RADIUS),
                    NavFavoring.EMPTY);
        }
    }

    public static final class Cache {
        private final Map<Key, Entry> verdicts = new LinkedHashMap<>();
        private final Map<Key, Probe> pending = new LinkedHashMap<>();

        public @Nullable Verdict verdict(UUID helper, BlockCoord from, BlockCoord target, long now) {
            Objects.requireNonNull(helper, "Помощник не может быть null: кэш адресный");
            prune(now);
            Entry entry = verdicts.get(new Key(helper, from, target));
            return entry == null ? null : entry.verdict();
        }

        public void request(
                NavWorld world,
                Predicate<BlockCoord> water,
                UUID helper,
                BlockCoord from,
                BlockCoord target,
                long now) {
            Objects.requireNonNull(world, "Мир навигации не может быть null: без него нет поиска");
            Objects.requireNonNull(water, "Проверка воды не может быть null: конец пути в воде — отказ");
            Key key = new Key(helper, from, target);
            if (verdict(helper, from, target, now) != null || pending.containsKey(key)) {
                return;
            }
            if (pending.size() >= MAX_PENDING) {
                return;
            }
            pending.put(key, new Probe(key, world, water, now));
        }

        public void advance(long now, long budgetNanos) {
            prune(now);
            if (pending.isEmpty() || budgetNanos <= 0) {
                return;
            }
            long slice = Math.max(1, budgetNanos / pending.size());
            Iterator<Map.Entry<Key, Probe>> iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                Probe probe = iterator.next().getValue();
                NavResult result = probe.session.step(slice);
                if (result != null) {
                    remember(probe, verdict(probe, result), now);
                    iterator.remove();
                } else if (now - probe.started >= PROBE_TTL_TICKS) {
                    remember(probe, new Verdict(false, "поиск не уложился: срок"), now);
                    iterator.remove();
                }
            }
        }

        private void remember(Probe probe, Verdict verdict, long now) {
            verdicts.put(probe.key, new Entry(verdict, now));
            if (verdicts.size() > MAX_VERDICTS) {
                Iterator<Key> oldest = verdicts.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }

        private void prune(long now) {
            verdicts.entrySet().removeIf(entry -> now - entry.getValue().at() >= VERDICT_TTL_TICKS);
        }

        private static Verdict verdict(Probe probe, NavResult result) {
            NavPath path = result.path();
            if (!result.found() || path == null) {
                return new Verdict(false, "путь не найден");
            }
            List<BlockCoord> cells = path.positions();
            BlockCoord end = cells.get(cells.size() - 1);
            String refusal = DescentPath.refusalOrNull(cells, probe.water.test(end));
            if (refusal != null) {
                return new Verdict(false, refusal);
            }
            return new Verdict(true, "путь " + cells.size() + " клеток");
        }
    }

    private RescueReach() {}
}
