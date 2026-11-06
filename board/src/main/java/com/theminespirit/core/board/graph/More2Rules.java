package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class More2Rules {
    public static final double LOW_FOOD_DAYS = 2.0;
    public static final int FREE_SLOTS_FULL = 4;
    public static final int FREE_SLOTS_ROOM = 8;
    public static final int LONG_CARRY_BLOCKS = 48;
    public static final int PATH_SPIRITS = 2;

    public static final String FOOD_LOW = "more2_food_low";
    public static final String INVENTORY_FULL = "more2_inventory_full";
    public static final String INVENTORY_SPACE = "more2_inventory_space";
    public static final String LONG_CARRY = "more2_long_carry";
    public static final String FIRE_NEAR_BUILDING = "more2_fire_near_building";
    public static final String LAVA_NEAR_BUILDING = "more2_lava_near_building";
    public static final String HAZARD_FLOW = "more2_hazard_flow";
    public static final String ANIMAL_OUTSIDE = "more2_animal_outside";
    public static final String WILD_HERD = "more2_wild_herd";
    public static final String VILLAGER_NEAR = "more2_villager_near";
    public static final String STAGE_PREP = "more2_stage_prep";
    public static final String BED_NEEDED = "more2_bed_needed";
    public static final String PATH_NEEDED = "more2_path_needed";

    private More2Rules() {}

    public record Food(int stock, int needPerDay, int producePerDay) {
        public Food {
            if (stock < 0 || needPerDay < 0 || producePerDay < 0) {
                throw new IllegalArgumentException("Еда " + stock + ", расход " + needPerDay + ", производство "
                        + producePerDay + " не бывают отрицательными: отсутствие — ноль");
            }
        }

        public double daysLeft() {
            int net = needPerDay - producePerDay;
            if (net <= 0) {
                return Double.POSITIVE_INFINITY;
            }
            return (double) stock / net;
        }

        public boolean low() {
            return daysLeft() < LOW_FOOD_DAYS;
        }
    }

    public record Inventory(int freeSlots, int freeChestSlots, boolean carries, boolean workPending) {
        public Inventory {
            if (freeSlots < 0 || freeChestSlots < 0) {
                throw new IllegalArgumentException("Свободные слоты " + freeSlots + "/" + freeChestSlots
                        + " не бывают отрицательными: полный инвентарь — ноль");
            }
        }

        public boolean full() {
            return carries && freeSlots <= FREE_SLOTS_FULL;
        }

        public boolean room() {
            return workPending && freeChestSlots > 0 && freeSlots >= FREE_SLOTS_ROOM;
        }
    }

    public record Carry(int distanceBlocks) {
        public Carry {
            if (distanceBlocks < 0) {
                throw new IllegalArgumentException(
                        "Дорога " + distanceBlocks + " не бывает отрицательной: рядом — ноль");
            }
        }

        public boolean longHaul() {
            return distanceBlocks > LONG_CARRY_BLOCKS;
        }
    }

    public record Hazards(int fire, int lavaNear, int flowingWater, int flowingLava) {
        public Hazards {
            if (fire < 0 || lavaNear < 0 || flowingWater < 0 || flowingLava < 0) {
                throw new IllegalArgumentException("Очаги " + fire + "/" + lavaNear + "/" + flowingWater + "/"
                        + flowingLava + " не бывают отрицательными: чисто — ноль");
            }
        }

        public boolean fireNearBuilding() {
            return fire > 0;
        }

        public boolean lavaNearBuilding() {
            return lavaNear > 0;
        }

        public boolean flow() {
            return flowingWater > 0 || flowingLava > 0;
        }
    }

    public record Animals(int total, int penned, int wildNearby, boolean penExists) {
        public Animals {
            if (total < 0 || penned < 0 || wildNearby < 0) {
                throw new IllegalArgumentException("Животные " + total + "/" + penned + "/" + wildNearby
                        + " не бывают отрицательными: пусто — ноль");
            }
            if (penned > total) {
                throw new IllegalArgumentException("В загоне " + penned + " из " + total
                        + ": загон не может держать больше животных, чем их есть");
            }
        }

        public int outside() {
            return total - penned;
        }

        public boolean outsidePen() {
            return penExists && outside() > 0;
        }

        public boolean wild() {
            return wildNearby > 0;
        }
    }

    public record Villagers(int count) {
        public Villagers {
            if (count < 0) {
                throw new IllegalArgumentException("Жителей " + count + " не бывает меньше нуля");
            }
        }

        public boolean nearby() {
            return count > 0;
        }
    }

    public record Stage(int stage, int stages, int idle) {
        public Stage {
            if (stage < 1 || stages < 0 || idle < 0) {
                throw new IllegalArgumentException("Этап " + stage + " из " + stages + ", свободных " + idle
                        + ": этап с первого, счётчики не отрицательны");
            }
            if (stages > 0 && stage > stages) {
                throw new IllegalArgumentException(
                        "Этап " + stage + " больше числа этапов " + stages + ": план поселения не знает такого этапа");
            }
        }

        public boolean prep() {
            return stage < stages && idle > 0;
        }
    }

    public record Sleep(int beds, int spirits) {
        public Sleep {
            if (beds < 0 || spirits < 0) {
                throw new IllegalArgumentException(
                        "Кровати " + beds + " и духи " + spirits + " не бывают отрицательными: пусто — ноль");
            }
        }

        public boolean needed() {
            return beds < spirits;
        }
    }

    public record Paths(int outside, int trails) {
        public Paths {
            if (outside < 0 || trails < 0) {
                throw new IllegalArgumentException(
                        "Духи снаружи " + outside + ", тропы " + trails + " не бывают отрицательными: нет — ноль");
            }
        }

        public boolean needed() {
            return trails == 0 && outside >= PATH_SPIRITS;
        }
    }

    public record Signals(
            Food food,
            Inventory inventory,
            Carry carry,
            Hazards hazards,
            Animals animals,
            Villagers villagers,
            Stage stage,
            Sleep sleep,
            Paths paths) {
        public Signals {
            Objects.requireNonNull(food, "Прогноз еды не может быть null: без расхода — Food(0, 0, 0)");
            Objects.requireNonNull(
                    inventory, "Место в руках не может быть null: пусто — Inventory(0, 0, false, false)");
            Objects.requireNonNull(carry, "Дорога до склада не может быть null: рядом — Carry(0)");
            Objects.requireNonNull(hazards, "Очаги не могут быть null: чисто — Hazards(0, 0, 0, 0)");
            Objects.requireNonNull(animals, "Животные не могут быть null: пусто — Animals(0, 0, 0, false)");
            Objects.requireNonNull(villagers, "Жители не могут быть null: никого — Villagers(0)");
            Objects.requireNonNull(stage, "Этап не может быть null: без подготовки — Stage(1, 0, 0)");
            Objects.requireNonNull(sleep, "Кровати не могут быть null: Sleep(0, 0)");
            Objects.requireNonNull(paths, "Тропы не могут быть null: Paths(0, 0)");
        }
    }

    public static Signals empty() {
        return new Signals(
                new Food(0, 0, 0),
                new Inventory(0, 0, false, false),
                new Carry(0),
                new Hazards(0, 0, 0, 0),
                new Animals(0, 0, 0, false),
                new Villagers(0),
                new Stage(1, 0, 0),
                new Sleep(0, 0),
                new Paths(0, 0));
    }

    public static Set<String> events(Signals signals) {
        Set<String> events = new LinkedHashSet<>();
        if (signals.food().low()) {
            events.add(FOOD_LOW);
        }
        if (signals.inventory().full()) {
            events.add(INVENTORY_FULL);
        }
        if (signals.inventory().room()) {
            events.add(INVENTORY_SPACE);
        }
        if (signals.carry().longHaul()) {
            events.add(LONG_CARRY);
        }
        if (signals.hazards().fireNearBuilding()) {
            events.add(FIRE_NEAR_BUILDING);
        }
        if (signals.hazards().lavaNearBuilding()) {
            events.add(LAVA_NEAR_BUILDING);
        }
        if (signals.hazards().flow()) {
            events.add(HAZARD_FLOW);
        }
        if (signals.animals().outsidePen()) {
            events.add(ANIMAL_OUTSIDE);
        }
        if (signals.animals().wild()) {
            events.add(WILD_HERD);
        }
        if (signals.villagers().nearby()) {
            events.add(VILLAGER_NEAR);
        }
        if (signals.stage().prep()) {
            events.add(STAGE_PREP);
        }
        if (signals.sleep().needed()) {
            events.add(BED_NEEDED);
        }
        if (signals.paths().needed()) {
            events.add(PATH_NEEDED);
        }
        return Set.copyOf(events);
    }
}
