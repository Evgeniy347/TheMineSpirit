package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.Set;

public final class MoreRules {
    public static final String PLAYER_NEAR = "more:player_near";
    public static final String PLAYER_BUILDING = "more:player_building";
    public static final String DARK_SPOTS = "more:dark_spots";
    public static final String DUSK_SOON = "more:dusk_soon";
    public static final String BAD_TRIP_CONDITIONS = "more:bad_trip_conditions";
    public static final String FAILURES_REPEAT = "more:failures_repeat";
    public static final String STATION_QUEUE = "more:station_queue";
    public static final String DROPS_EXPIRING = "more:drops_expiring";
    public static final String FOREST_DEPLETED = "more:forest_depleted";
    public static final String ORE_DEPLETED = "more:ore_depleted";
    public static final String FENCE_GAP = "more:fence_gap";
    public static final String DOOR_OPEN_NIGHT = "more:door_open_night";
    public static final String HOPPER_UNCOVERED = "more:hopper_uncovered";
    public static final String RESERVE_UNUSED = "more:reserve_unused";
    public static final String WORK_DUPLICATE = "more:work_duplicate";
    public static final String STRATEGIC_GOAL = "more:strategic_goal";
    private static final int FAILURE_REPEATS = 2;
    private static final int STATION_WAITERS = 2;
    private static final int TREES_LOW = 4;

    public record Player(boolean near, boolean holdingBlock, boolean usingItem, boolean digging) {
        public Player(boolean near, boolean holdingBlock, boolean usingItem) {
            this(near, holdingBlock, usingItem, false);
        }
    }

    public record Clock(boolean duskSoon, boolean badWeather) {}

    public record Drops(int total, int expiring) {
        public Drops {
            if (total < 0 || expiring < 0 || expiring > total) {
                throw new IllegalArgumentException(
                        "Дропы " + total + "/" + expiring + ": исчезающих не больше всех, отрицательных не бывает");
            }
        }
    }

    public record Resources(int trees, int plantedSaplings, int saplingStock, int ore, boolean mineBuilt) {
        public Resources {
            if (trees < 0 || plantedSaplings < 0 || saplingStock < 0 || ore < 0) {
                throw new IllegalArgumentException("Ресурсы " + trees + "/" + plantedSaplings + "/" + saplingStock + "/"
                        + ore + " не бывают отрицательными: отсутствие — ноль");
            }
        }
    }

    public record Security(int fenceGaps, int openDoors, int hoppers, boolean night) {
        public Security {
            if (fenceGaps < 0 || openDoors < 0 || hoppers < 0) {
                throw new IllegalArgumentException("Защита " + fenceGaps + "/" + openDoors + "/" + hoppers
                        + " не бывает отрицательной: отсутствие — ноль");
            }
        }
    }

    public record Reserves(int unprotected, int spareArmor, int duplicateActivities) {
        public Reserves {
            if (unprotected < 0 || spareArmor < 0 || duplicateActivities < 0) {
                throw new IllegalArgumentException("Резервы " + unprotected + "/" + spareArmor + "/"
                        + duplicateActivities + " не бывают отрицательными: отсутствие — ноль");
            }
        }
    }

    public record Signals(
            Player player,
            int darkCells,
            Clock clock,
            int repeatedFailures,
            int waitingAtStations,
            Drops drops,
            Resources resources,
            Security security,
            Reserves reserves,
            int strategicGoals) {
        public Signals {
            if (player == null
                    || clock == null
                    || drops == null
                    || resources == null
                    || security == null
                    || reserves == null) {
                throw new IllegalArgumentException("Сигналы more без обязательной части: пустые задают нулями");
            }
            if (darkCells < 0 || repeatedFailures < 0 || waitingAtStations < 0 || strategicGoals < 0) {
                throw new IllegalArgumentException("Счётчики more " + darkCells + "/" + repeatedFailures + "/"
                        + waitingAtStations + "/" + strategicGoals + " не бывают отрицательными");
            }
        }
    }

    private MoreRules() {}

    public static Set<String> signals(Signals signals) {
        Set<String> tags = new LinkedHashSet<>();
        player(tags, signals.player());
        light(tags, signals.darkCells());
        clock(tags, signals.clock());
        failures(tags, signals.repeatedFailures());
        queue(tags, signals.waitingAtStations());
        drops(tags, signals.drops());
        resources(tags, signals.resources());
        security(tags, signals.security());
        reserves(tags, signals.reserves());
        strategy(tags, signals.strategicGoals());
        return Set.copyOf(tags);
    }

    private static void player(Set<String> tags, Player player) {
        if (!player.near()) {
            return;
        }
        tags.add(PLAYER_NEAR);
        if (player.holdingBlock() || player.usingItem() || player.digging()) {
            tags.add(PLAYER_BUILDING);
        }
    }

    private static void light(Set<String> tags, int darkCells) {
        if (darkCells > 0) {
            tags.add(DARK_SPOTS);
            tags.add("more:dark:" + darkCells);
        }
    }

    private static void clock(Set<String> tags, Clock clock) {
        if (clock.duskSoon()) {
            tags.add(DUSK_SOON);
        }
        if (clock.duskSoon() || clock.badWeather()) {
            tags.add(BAD_TRIP_CONDITIONS);
        }
    }

    private static void failures(Set<String> tags, int repeatedFailures) {
        if (repeatedFailures >= FAILURE_REPEATS) {
            tags.add(FAILURES_REPEAT);
            tags.add("more:failures:" + repeatedFailures);
        }
    }

    private static void queue(Set<String> tags, int waiting) {
        if (waiting >= STATION_WAITERS) {
            tags.add(STATION_QUEUE);
            tags.add("more:queue:" + waiting);
        }
    }

    private static void drops(Set<String> tags, Drops drops) {
        if (drops.expiring() > 0) {
            tags.add(DROPS_EXPIRING);
            tags.add("more:drops:" + drops.expiring());
        }
    }

    private static void resources(Set<String> tags, Resources resources) {
        if (resources.trees() < TREES_LOW && resources.plantedSaplings() == 0 && resources.saplingStock() > 0) {
            tags.add(FOREST_DEPLETED);
            tags.add("more:trees:" + resources.trees());
        }
        if (resources.mineBuilt() && resources.ore() == 0) {
            tags.add(ORE_DEPLETED);
        }
    }

    private static void security(Set<String> tags, Security security) {
        if (security.fenceGaps() > 0) {
            tags.add(FENCE_GAP);
            tags.add("more:fence_gaps:" + security.fenceGaps());
        }
        if (security.night() && security.openDoors() > 0) {
            tags.add(DOOR_OPEN_NIGHT);
            tags.add("more:open_doors:" + security.openDoors());
        }
        if (security.hoppers() > 0) {
            tags.add(HOPPER_UNCOVERED);
            tags.add("more:hoppers:" + security.hoppers());
        }
    }

    private static void reserves(Set<String> tags, Reserves reserves) {
        if (reserves.unprotected() > 0 && reserves.spareArmor() > 0) {
            tags.add(RESERVE_UNUSED);
            tags.add("more:spare_armor:" + reserves.spareArmor());
        }
        if (reserves.duplicateActivities() > 0) {
            tags.add(WORK_DUPLICATE);
            tags.add("more:duplicates:" + reserves.duplicateActivities());
        }
    }

    private static void strategy(Set<String> tags, int strategicGoals) {
        if (strategicGoals > 0) {
            tags.add(STRATEGIC_GOAL);
            tags.add("more:goals:" + strategicGoals);
        }
    }
}
