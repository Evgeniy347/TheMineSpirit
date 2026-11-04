package com.theminespirit.core.board.graph;

import com.theminespirit.core.inventory.WarehouseCategories;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class More3Rules {
    public static final long DANGER_WINDOW_TICKS = 12_000L;
    public static final long DROP_LIFETIME_TICKS = 6_000L;
    public static final long STAGE_BUDGET_TICKS = 24_000L;
    public static final String TRASH = "#trash";
    public static final String WORK_YIELD_LOW = "work_yield_low";
    public static final String WORK_CONFLICT = "work_conflict";
    public static final String STATION_IDLE = "station_idle";
    public static final String PLAYER_SLEEP = "player_sleep";
    public static final String STAGE_SLOW = "stage_slow";
    private static final String OTHER_CATEGORY = "прочее";
    private static final int CONFLICT_RADIUS = 4;
    private static final int SLOW_FACTOR = 2;
    private static final int SLOW_SLACK = 200;
    private static final WarehouseCategories CATEGORIES = WarehouseCategories.load();

    public record Signals(
            int dangerSpots,
            int deathDrops,
            int slowWorks,
            int trashItems,
            int unlabeledChests,
            int workConflicts,
            int idleStations,
            boolean playerSleeping,
            int collapsingSand,
            int collapsingGravel,
            int trampledFarmland,
            boolean leavesDecaying,
            boolean stageSlow) {
        public Signals {
            for (Map.Entry<String, Integer> count : Map.of(
                            "опасных мест",
                            dangerSpots,
                            "дропов после смерти",
                            deathDrops,
                            "медленных работ",
                            slowWorks,
                            "мусора",
                            trashItems,
                            "беспризорных сундуков",
                            unlabeledChests,
                            "конфликтов работ",
                            workConflicts,
                            "простаивающих станций",
                            idleStations,
                            "осыпающегося песка",
                            collapsingSand,
                            "осыпающегося гравия",
                            collapsingGravel,
                            "вытоптанных грядок",
                            trampledFarmland)
                    .entrySet()) {
                if (count.getValue() < 0) {
                    throw new IllegalArgumentException("Число " + count.getKey() + " = " + count.getValue()
                            + " отрицательно: отсутствие задают нулём");
                }
            }
        }
    }

    private More3Rules() {}

    public static Set<String> events(Signals signals) {
        Set<String> events = new LinkedHashSet<>();
        add(events, signals.dangerSpots() > 0, "danger_spot");
        add(events, signals.deathDrops() > 0, "spirit_death_drop");
        add(events, signals.slowWorks() > 0, WORK_YIELD_LOW);
        add(events, signals.trashItems() > 0, "trash_found");
        add(events, signals.unlabeledChests() > 0, "chest_purpose_missing");
        add(events, signals.workConflicts() > 0, WORK_CONFLICT);
        add(events, signals.idleStations() > 0, STATION_IDLE);
        add(events, signals.playerSleeping(), PLAYER_SLEEP);
        add(events, signals.collapsingSand() > 0, "sand_collapse");
        add(events, signals.collapsingGravel() > 0, "gravel_collapse");
        add(events, signals.trampledFarmland() > 0, "farmland_trampled");
        add(events, signals.leavesDecaying(), "world_decay");
        add(events, signals.stageSlow(), STAGE_SLOW);
        return Set.copyOf(events);
    }

    public static int trash(SupplyGraph graph, Map<String, Integer> items) {
        Set<String> known = new HashSet<>(graph.members(TRASH));
        return items.entrySet().stream()
                .filter(entry -> known.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public static int unlabeledChests(List<GraphFacts.Chest> chests) {
        int unlabeled = 0;
        for (GraphFacts.Chest chest : chests) {
            if (chest.contents().isEmpty()) {
                continue;
            }
            Set<String> categories = new HashSet<>();
            chest.contents().keySet().forEach(item -> categories.add(CATEGORIES.categoryOf(item)));
            if (categories.size() != 1 || categories.contains(OTHER_CATEGORY)) {
                unlabeled++;
            }
        }
        return unlabeled;
    }

    public static int workConflicts(Collection<GraphFacts.Spirit> spirits) {
        List<GraphFacts.Spirit> working =
                spirits.stream().filter(spirit -> spirit.activity() != null).toList();
        int conflicts = 0;
        for (int first = 0; first < working.size(); first++) {
            for (int second = first + 1; second < working.size(); second++) {
                GraphFacts.Spirit one = working.get(first);
                GraphFacts.Spirit two = working.get(second);
                if (!Objects.equals(one.activity(), two.activity()) && near(one.position(), two.position())) {
                    conflicts++;
                }
            }
        }
        return conflicts;
    }

    public static int idleStations(Map<String, GraphFacts.Station> stations, Set<String> busy) {
        return (int) stations.values().stream()
                .filter(station -> !station.busy())
                .filter(station -> !busy.contains(station.id()))
                .count();
    }

    public static boolean workSlow(long actualTicks, int expectedTicks) {
        return expectedTicks > 0 && actualTicks > (long) expectedTicks * SLOW_FACTOR + SLOW_SLACK;
    }

    public static long stageBudget(int stage) {
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + " меньше 1: бюджет этапа начинается с первого");
        }
        return STAGE_BUDGET_TICKS * stage;
    }

    public static boolean stageSlow(long elapsedTicks, int stage) {
        return elapsedTicks > stageBudget(stage);
    }

    private static boolean near(GraphFacts.Position one, GraphFacts.Position two) {
        int dx = one.x() - two.x();
        int dy = one.y() - two.y();
        int dz = one.z() - two.z();
        return dx * dx + dy * dy + dz * dz <= CONFLICT_RADIUS * CONFLICT_RADIUS;
    }

    private static void add(Set<String> events, boolean present, String name) {
        if (present) {
            events.add(name);
        }
    }
}
