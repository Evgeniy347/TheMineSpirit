package com.theminespirit.core.board.graph;

import com.theminespirit.core.crafting.OrderBook;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class More7Rules {
    public static final long DAY_TICKS = 24_000L;
    public static final int MORNING_END = 4_000;
    public static final int EVENING_START = 10_000;
    public static final int EVENING_END = 14_000;
    public static final int PILEUP_SPIRITS = 2;

    public static final String WORK_PILEUP = "more7_work_pileup";
    public static final String GATED_ITEM = "more7_gated_item";
    public static final String MORNING_HARVEST = "more7_morning_harvest";
    public static final String EVENING_RETURN = "more7_evening_return";
    public static final String PEN_FULL = "more7_pen_full";
    public static final String FIELD_FULL = "more7_field_full";
    public static final String ORDER_READY = "more7_order_ready";

    public enum DayPhase {
        MORNING,
        DAY,
        EVENING,
        NIGHT
    }

    public record Workload(int working, int busiest, int pendingNeeds) {
        public Workload {
            if (working < 0 || busiest < 0 || pendingNeeds < 0) {
                throw new IllegalArgumentException("Работающих " + working + ", на одной задаче " + busiest
                        + ", висящих нужд " + pendingNeeds + ": счётчики не бывают отрицательными");
            }
            if (busiest > working) {
                throw new IllegalArgumentException("На одной задаче " + busiest + " из " + working
                        + " работающих: одна задача не может собрать больше духов, чем работает");
            }
        }

        public boolean piled() {
            return working >= PILEUP_SPIRITS && busiest == working && pendingNeeds > 0;
        }
    }

    public record Capacity(int used, int limit) {
        public Capacity {
            if (used < 0 || limit < 0) {
                throw new IllegalArgumentException(
                        "Занято " + used + " из " + limit + ": счётчики не бывают отрицательными");
            }
        }

        public boolean full() {
            return limit > 0 && used >= limit;
        }
    }

    public record Day(boolean morning, boolean evening, int harvestReady, int outside) {
        public Day {
            if (harvestReady < 0 || outside < 0) {
                throw new IllegalArgumentException("Готовый урожай " + harvestReady + ", духов снаружи " + outside
                        + ": счётчики не бывают отрицательными");
            }
        }
    }

    public record Signals(Workload work, int gatedItems, Day day, Capacity pen, Capacity field, int readyOrders) {
        public Signals {
            Objects.requireNonNull(work, "Нагрузка духов не может быть null: без работы — Workload(0, 0, 0)");
            Objects.requireNonNull(day, "Время суток не может быть null: без пиков — Day(false, false, 0, 0)");
            Objects.requireNonNull(pen, "Загон не может быть null: без загона — Capacity(0, 0)");
            Objects.requireNonNull(field, "Поле не может быть null: без поля — Capacity(0, 0)");
            if (gatedItems < 0 || readyOrders < 0) {
                throw new IllegalArgumentException("Закрытых этапом предметов " + gatedItems + ", готовых заказов "
                        + readyOrders + ": счётчики не бывают отрицательными");
            }
        }
    }

    private More7Rules() {}

    public static DayPhase phase(long dayTime) {
        long time = Math.floorMod(dayTime, DAY_TICKS);
        if (time < MORNING_END) {
            return DayPhase.MORNING;
        }
        if (time < EVENING_START) {
            return DayPhase.DAY;
        }
        if (time < EVENING_END) {
            return DayPhase.EVENING;
        }
        return DayPhase.NIGHT;
    }

    public static Workload workload(Collection<GraphFacts.Spirit> spirits, int pendingNeeds) {
        Map<String, Integer> byActivity = new LinkedHashMap<>();
        int working = 0;
        for (GraphFacts.Spirit spirit : spirits) {
            String activity = spirit.activity();
            if (activity == null || activity.isBlank()) {
                continue;
            }
            working++;
            byActivity.merge(activity, 1, Integer::sum);
        }
        int busiest =
                byActivity.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new Workload(working, busiest, pendingNeeds);
    }

    public static boolean needsLater(SupplyGraph graph, int stage, String node) {
        Objects.requireNonNull(graph, "Граф не может быть null: без него не узнать этап предмета");
        Objects.requireNonNull(node, "Узел не может быть null: этап спрашивают у конкретного предмета");
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + " меньше 1: поселение начинается с первого");
        }
        SupplyGraph.Item item = graph.items().get(node);
        if (item != null && item.stage() > stage) {
            return true;
        }
        if (graph.isTool(node)) {
            int split = node.indexOf('_');
            String tier = node.substring(0, split);
            String kind = node.substring(split + 1);
            return graph.rank(tier) > graph.rank(graph.kit().tier(kind, stage));
        }
        if (graph.isGroup(node)) {
            return false;
        }
        return producersLater(graph, stage, node);
    }

    public static Set<String> gated(SupplyGraph graph, int stage, Collection<String> wanted) {
        Set<String> gated = new LinkedHashSet<>();
        for (String node : wanted) {
            if (needsLater(graph, stage, node)) {
                gated.add(node);
            }
        }
        return Set.copyOf(gated);
    }

    public static int readyOrders(Collection<OrderBook.Order> orders) {
        return (int) orders.stream()
                .filter(order -> order.status() == OrderBook.Status.WAITING)
                .filter(order -> order.missing().isEmpty())
                .count();
    }

    public static Map<String, Integer> liveOrders(SupplyGraph graph, int stage, Map<String, Integer> orders) {
        Map<String, Integer> live = new LinkedHashMap<>(orders);
        live.keySet().removeAll(gated(graph, stage, orders.keySet()));
        return Map.copyOf(live);
    }

    public static GraphState merge(GraphState state, Set<String> more, Map<String, Integer> orders) {
        Set<String> events = new LinkedHashSet<>(state.events());
        events.addAll(more);
        return new GraphState(
                state.stock(),
                state.stage(),
                state.kits(),
                state.buildings(),
                state.materials(),
                state.storageFreePercent(),
                Set.copyOf(events),
                state.world(),
                state.distances(),
                state.stations(),
                Map.copyOf(orders),
                state.facts());
    }

    public static Set<String> events(Signals signals) {
        Set<String> events = new LinkedHashSet<>();
        add(events, signals.work().piled(), WORK_PILEUP);
        add(events, signals.gatedItems() > 0, GATED_ITEM);
        add(events, signals.day().morning() && signals.day().harvestReady() > 0, MORNING_HARVEST);
        add(events, signals.day().evening() && signals.day().outside() > 0, EVENING_RETURN);
        add(events, signals.pen().full(), PEN_FULL);
        add(events, signals.field().full(), FIELD_FULL);
        add(events, signals.readyOrders() > 0, ORDER_READY);
        return Set.copyOf(events);
    }

    private static boolean producersLater(SupplyGraph graph, int stage, String node) {
        var producers = graph.producers(node);
        if (producers.isEmpty()) {
            return false;
        }
        return producers.stream().noneMatch(way -> {
            if (way.building() == null) {
                return true;
            }
            SupplyGraph.Building building = graph.buildings().get(way.building());
            return building != null && building.stage() <= stage;
        });
    }

    private static void add(Set<String> events, boolean present, String name) {
        if (present) {
            events.add(name);
        }
    }
}
