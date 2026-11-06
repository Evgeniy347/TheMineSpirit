package com.theminespirit.core.board.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class More6Rules {
    public static final int WEAK_HEALTH = 12;
    public static final int ARMOR_DURABILITY = 20;
    public static final int FAR_BLOCKS = 48;
    public static final int THREAT_RADIUS = 16;
    public static final int IDLE_TICKS = 200;
    public static final int LOOSE_ITEMS = 4;
    public static final int REPEAT_BUILDINGS = 3;
    public static final int WATER_REACH = 8;

    public static final String SPIRITS_SHORT = "more6_spirits_short";
    public static final String WEAK_FAR = "more6_weak_far";
    public static final String BUILD_UNFINISHED = "more6_build_unfinished";
    public static final String CARRY_MATERIALS = "more6_carry_materials";
    public static final String STATION_BROKEN = "more6_station_broken";
    public static final String LOOSE_ITEMS_FOUND = "more6_loose_items";
    public static final String WATER_NEEDED = "more6_water_needed";
    public static final String LAVA_NEEDED = "more6_lava_needed";
    public static final String REPEAT_DEMAND = "more6_repeat_demand";
    public static final String SPIRIT_IDLE = "more6_spirit_idle";

    public record Population(int spirits, int housing) {
        public Population {
            nonNegative(spirits, housing);
        }

        public boolean shortage() {
            return spirits < housing;
        }
    }

    public record Risk(int weakFar, int weakThreat) {
        public Risk {
            nonNegative(weakFar, weakThreat);
        }

        public boolean present() {
            return weakFar + weakThreat > 0;
        }
    }

    public record Unfinished(int partial) {
        public Unfinished {
            nonNegative(partial);
        }

        public boolean present() {
            return partial > 0;
        }
    }

    public record Carried(int materials) {
        public Carried {
            nonNegative(materials);
        }

        public boolean present() {
            return materials > 0;
        }
    }

    public record Stations(int unfuelled, int misplaced) {
        public Stations {
            nonNegative(unfuelled, misplaced);
        }

        public boolean broken() {
            return unfuelled + misplaced > 0;
        }
    }

    public record Loose(int items) {
        public Loose {
            nonNegative(items);
        }

        public boolean present() {
            return items >= LOOSE_ITEMS;
        }
    }

    public record Fluids(int dryFields, int lavaSources) {
        public Fluids {
            nonNegative(dryFields, lavaSources);
        }

        public boolean water() {
            return dryFields > 0;
        }

        public boolean lava() {
            return lavaSources > 0;
        }
    }

    public record Demand(int repeated) {
        public Demand {
            nonNegative(repeated);
        }

        public boolean present() {
            return repeated > 0;
        }
    }

    public record Idle(int longestTicks, int idleSpirits) {
        public Idle {
            nonNegative(longestTicks, idleSpirits);
        }

        public boolean present() {
            return idleSpirits > 0 && longestTicks >= IDLE_TICKS;
        }
    }

    public record Signals(
            Population population,
            Risk risk,
            Unfinished unfinished,
            Carried carried,
            Stations stations,
            Loose loose,
            Fluids fluids,
            Demand demand,
            Idle idle) {
        public Signals {
            Objects.requireNonNull(population, "Духи и жильё не могут быть null: пусто — Population(0, 0)");
            Objects.requireNonNull(risk, "Риск не может быть null: спокойно — Risk(0, 0)");
            Objects.requireNonNull(unfinished, "Незаконченные постройки не могут быть null: нет — Unfinished(0)");
            Objects.requireNonNull(carried, "Материалы в руках не могут быть null: пусто — Carried(0)");
            Objects.requireNonNull(stations, "Станции не могут быть null: исправны — Stations(0, 0)");
            Objects.requireNonNull(loose, "Предметы на земле не могут быть null: чисто — Loose(0)");
            Objects.requireNonNull(fluids, "Вода и лава не могут быть null: нет — Fluids(0, 0)");
            Objects.requireNonNull(demand, "Повторные запросы не могут быть null: нет — Demand(0)");
            Objects.requireNonNull(idle, "Простой не может быть null: заняты — Idle(0, 0)");
        }
    }

    private More6Rules() {}

    public static Signals empty() {
        return new Signals(
                new Population(0, 0),
                new Risk(0, 0),
                new Unfinished(0),
                new Carried(0),
                new Stations(0, 0),
                new Loose(0),
                new Fluids(0, 0),
                new Demand(0),
                new Idle(0, 0));
    }

    public static Set<String> events(Signals signals) {
        Objects.requireNonNull(signals, "Вход области не может быть null: пусто — More6Rules.empty()");
        Set<String> events = new LinkedHashSet<>();
        add(events, signals.population().shortage(), SPIRITS_SHORT);
        add(events, signals.risk().present(), WEAK_FAR);
        add(events, signals.unfinished().present(), BUILD_UNFINISHED);
        add(events, signals.carried().present(), CARRY_MATERIALS);
        add(events, signals.stations().broken(), STATION_BROKEN);
        add(events, signals.loose().present(), LOOSE_ITEMS_FOUND);
        add(events, signals.fluids().water(), WATER_NEEDED);
        add(events, signals.fluids().lava() && signals.stations().unfuelled() > 0, LAVA_NEEDED);
        add(events, signals.demand().present(), REPEAT_DEMAND);
        add(events, signals.idle().present(), SPIRIT_IDLE);
        return Set.copyOf(events);
    }

    public static Population population(int spirits, int housing) {
        return new Population(spirits, housing);
    }

    public static Risk risk(
            Collection<GraphFacts.Spirit> spirits, GraphFacts.Position home, Collection<GraphFacts.Threat> threats) {
        Objects.requireNonNull(spirits, "Духи не могут быть null: пусто — пустой список");
        Objects.requireNonNull(home, "Дом не может быть null: от него считается дальность");
        Objects.requireNonNull(threats, "Угрозы не могут быть null: спокойно — пустой список");
        int far = 0;
        int threatened = 0;
        for (GraphFacts.Spirit spirit : spirits) {
            if (!weak(spirit)) {
                continue;
            }
            if (distance(spirit.position(), home) > FAR_BLOCKS) {
                far++;
            }
            if (threatNear(spirit.position(), threats)) {
                threatened++;
            }
        }
        return new Risk(far, threatened);
    }

    public static Unfinished unfinished(Map<String, GraphFacts.Building> buildings) {
        Objects.requireNonNull(buildings, "Постройки не могут быть null: пусто — пустая карта");
        int partial = 0;
        for (GraphFacts.Building building : buildings.values()) {
            if (!building.unknown() && building.auditPercent() > 0 && building.auditPercent() < 100) {
                partial++;
            }
        }
        return new Unfinished(partial);
    }

    public static Carried carried(Map<String, Integer> carried, Set<String> needed) {
        Objects.requireNonNull(carried, "Переносимое не может быть null: пусто — пустая карта");
        Objects.requireNonNull(needed, "Нужные материалы не могут быть null: нет — пустой набор");
        int materials = 0;
        for (Map.Entry<String, Integer> entry : carried.entrySet()) {
            if (needed.contains(entry.getKey())) {
                materials += entry.getValue();
            }
        }
        return new Carried(materials);
    }

    public static Stations stations(int unfuelled, int misplaced) {
        return new Stations(unfuelled, misplaced);
    }

    public static Loose loose(int items) {
        return new Loose(items);
    }

    public static Fluids fluids(int dryFields, int lavaSources) {
        return new Fluids(dryFields, lavaSources);
    }

    public static Demand demand(Map<String, Map<String, Integer>> materials) {
        Objects.requireNonNull(materials, "Материалы построек не могут быть null: пусто — пустая карта");
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Integer> need : materials.values()) {
            for (String item : need.keySet()) {
                counts.merge(item, 1, Integer::sum);
            }
        }
        int repeated = (int) counts.values().stream()
                .filter(count -> count >= REPEAT_BUILDINGS)
                .count();
        return new Demand(repeated);
    }

    public static Idle idle(int longestTicks, int idleSpirits) {
        return new Idle(longestTicks, idleSpirits);
    }

    private static boolean weak(GraphFacts.Spirit spirit) {
        return spirit.health() <= WEAK_HEALTH || armorBroken(spirit.armor());
    }

    private static boolean armorBroken(Collection<GraphFacts.Armor> armor) {
        if (armor.isEmpty()) {
            return false;
        }
        int durability = armor.stream().mapToInt(GraphFacts.Armor::durability).sum();
        return durability <= ARMOR_DURABILITY;
    }

    private static boolean threatNear(GraphFacts.Position position, Collection<GraphFacts.Threat> threats) {
        for (GraphFacts.Threat threat : threats) {
            if (distance(position, threat.position()) <= THREAT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static int distance(GraphFacts.Position first, GraphFacts.Position second) {
        int dx = first.x() - second.x();
        int dy = first.y() - second.y();
        int dz = first.z() - second.z();
        return (int) Math.round(Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz));
    }

    private static void nonNegative(int... values) {
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "Счётчик " + value + " отрицателен: отсутствие задают нулём, а не минусом");
            }
        }
    }

    private static void add(Set<String> events, boolean present, String name) {
        if (present) {
            events.add(name);
        }
    }
}
