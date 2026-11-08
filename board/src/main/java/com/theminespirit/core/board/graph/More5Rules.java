package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class More5Rules {
    public static final long BROKEN_WINDOW_TICKS = 12_000L;
    public static final String FUEL = "#more5_fuel";
    public static final int ROUTE_FLUID_WIDTH = 2;
    public static final int CHEST_WORK_RADIUS = 24;
    public static final int MINE_DARK_MIN = 4;
    private static final int FUEL_FLOOR = 16;
    private static final int FUEL_PER_FURNACE = 8;

    public record Signals(
            int darkCrops,
            int darkSaplings,
            int dryCrops,
            int unfedGrowth,
            int fuelStock,
            int fuelTarget,
            boolean furnacePresent,
            int buildingShortage,
            int mobBrokenBlocks,
            int cutRoutes,
            int farChests,
            int darkMineCells,
            int woodDrops) {
        public Signals {
            for (Map.Entry<String, Integer> count : Map.ofEntries(
                            Map.entry("тёмных грядок", darkCrops),
                            Map.entry("тёмных саженцев", darkSaplings),
                            Map.entry("сухих грядок", dryCrops),
                            Map.entry("неудобренных посадок", unfedGrowth),
                            Map.entry("запаса топлива", fuelStock),
                            Map.entry("цели по топливу", fuelTarget),
                            Map.entry("нехватки стройматериалов", buildingShortage),
                            Map.entry("блоков, сломанных мобами", mobBrokenBlocks),
                            Map.entry("перерезанных путей", cutRoutes),
                            Map.entry("далёких мест работы", farChests),
                            Map.entry("тёмных клеток шахты", darkMineCells),
                            Map.entry("дропов после рубки", woodDrops))
                    .entrySet()) {
                if (count.getValue() < 0) {
                    throw new IllegalArgumentException("Число " + count.getKey() + " = " + count.getValue()
                            + " отрицательно: отсутствие задают нулём");
                }
            }
        }
    }

    private More5Rules() {}

    public static Set<String> events(Signals signals) {
        Set<String> events = new LinkedHashSet<>();
        add(events, signals.darkCrops() > 0, "more5_crops_dark");
        add(events, signals.darkCrops() > 0, "more5_crops_dark");
        add(events, signals.darkSaplings() > 0, "more5_saplings_dark");
        add(events, signals.dryCrops() > 0, "more5_crops_dry");
        add(events, signals.unfedGrowth() > 0, "more5_growth_unfed");
        add(events, signals.furnacePresent() && signals.fuelStock() < signals.fuelTarget(), "more5_fuel_low");
        add(events, signals.buildingShortage() > 0, "more5_build_shortage");
        add(events, signals.mobBrokenBlocks() > 0, "more5_mob_broken");
        add(events, signals.cutRoutes() > 0, "more5_route_blocked");
        add(events, signals.farChests() > 0, "more5_chest_far");
        add(events, signals.darkMineCells() > 0, "more5_mine_dark");
        add(events, signals.woodDrops() > 0, "more5_wood_drops");
        return Set.copyOf(events);
    }

    public static int fuelTarget(int furnaces) {
        if (furnaces < 0) {
            throw new IllegalArgumentException("Печей " + furnaces + " отрицательное число: их не бывает минус");
        }
        return Math.max(FUEL_FLOOR, FUEL_PER_FURNACE * furnaces);
    }

    public static int fuel(SupplyGraph graph, Map<String, Integer> stock) {
        Objects.requireNonNull(graph, "Граф не может быть null: по нему ищется группа топлива");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой — пустая карта");
        return graph.members(FUEL).stream()
                .mapToInt(item -> stock.getOrDefault(item, 0))
                .sum();
    }

    public static int buildShortage(
            Map<String, Map<String, Integer>> materials, Map<String, Integer> stock, Set<String> built) {
        Objects.requireNonNull(materials, "Материалы не могут быть null: без схемы — пустая карта");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой — пустая карта");
        Objects.requireNonNull(built, "Построенное не может быть null: без построек — пустой набор");
        int shortage = 0;
        for (Map.Entry<String, Map<String, Integer>> building : materials.entrySet()) {
            if (built.contains(building.getKey())) {
                continue;
            }
            for (Map.Entry<String, Integer> item : building.getValue().entrySet()) {
                int need = item.getValue();
                int have = stock.getOrDefault(item.getKey(), 0);
                if (have < need) {
                    shortage += need - have;
                }
            }
        }
        return shortage;
    }

    public static boolean cutByFluid(List<Boolean> line, int minWidth) {
        Objects.requireNonNull(line, "Линия пути не может быть null: пустая линия — пустой список");
        if (minWidth < 1) {
            throw new IllegalArgumentException("Ширина препятствия " + minWidth + " меньше 1: путь не режется нулём");
        }
        int start = -1;
        for (int index = 0; index <= line.size(); index++) {
            boolean fluid = index < line.size() && Boolean.TRUE.equals(line.get(index));
            if (fluid && start < 0) {
                start = index;
            } else if (!fluid && start >= 0) {
                boolean whole = start == 0 && index == line.size();
                if (index - start >= minWidth && !whole) {
                    return true;
                }
                start = -1;
            }
        }
        return false;
    }

    public static int farSites(List<GraphFacts.Position> sites, List<GraphFacts.Position> chests, int radius) {
        Objects.requireNonNull(sites, "Места работы не могут быть null: пустой список — работ нет");
        Objects.requireNonNull(chests, "Сундуки не могут быть null: пустой список — сундуков нет");
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус " + radius + " отрицателен: близость начинается с нуля");
        }
        long limit = (long) radius * radius;
        int far = 0;
        for (GraphFacts.Position site : sites) {
            boolean near = false;
            for (GraphFacts.Position chest : chests) {
                long dx = (long) site.x() - chest.x();
                long dy = (long) site.y() - chest.y();
                long dz = (long) site.z() - chest.z();
                if (dx * dx + dy * dy + dz * dz <= limit) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                far++;
            }
        }
        return far;
    }

    public static int woodDrops(Set<String> dropped, Set<String> saplings) {
        Objects.requireNonNull(dropped, "Дропы не могут быть null: без дропов — пустой набор");
        Objects.requireNonNull(saplings, "Саженцы не могут быть null: без группы — пустой набор");
        int found = 0;
        for (String item : dropped) {
            if (saplings.contains(item) || "apple".equals(item)) {
                found++;
            }
        }
        return found;
    }

    private static void add(Set<String> events, boolean present, String name) {
        if (present) {
            events.add(name);
        }
    }
}
