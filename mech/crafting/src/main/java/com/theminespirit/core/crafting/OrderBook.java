package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public final class OrderBook {
    public static final int MAX_WAITING_PER_PLAYER = 8;
    public static final int KEEP_FINISHED = 16;

    public enum Status {
        WAITING,
        DONE,
        CANCELLED
    }

    public record Order(
            int id,
            String player,
            String playerName,
            String recipe,
            String item,
            int crafts,
            Status status,
            Map<String, Integer> missing,
            String where) {
        public Order {
            Objects.requireNonNull(player, "Заказчик не может быть null: о готовности пишут ему");
            Objects.requireNonNull(playerName, "Имя заказчика не может быть null");
            Objects.requireNonNull(recipe, "Рецепт заказа не может быть null");
            Objects.requireNonNull(item, "Предмет заказа не может быть null");
            Objects.requireNonNull(status, "Статус заказа не может быть null");
            Objects.requireNonNull(where, "Место готового заказа не может быть null: пока не готов — пустая строка");
            missing = Map.copyOf(Objects.requireNonNull(missing, "Недостача заказа не может быть null"));
            if (crafts <= 0) {
                throw new IllegalArgumentException("Заказ " + id + " на " + crafts + " крафтов: делать нечего");
            }
        }

        Order with(Status next, Map<String, Integer> lack, String at) {
            return new Order(id, player, playerName, recipe, item, crafts, next, lack, at);
        }
    }

    private final List<Order> orders = new ArrayList<>();
    private int nextId = 1;

    public static OrderBook of(List<Order> saved, int nextId) {
        OrderBook book = new OrderBook();
        book.orders.addAll(saved);
        book.nextId = Math.max(nextId, saved.stream().mapToInt(Order::id).max().orElse(0) + 1);
        return book;
    }

    public Optional<Order> place(
            String player, String playerName, String recipe, String item, int crafts, Map<String, Integer> missing) {
        long waiting = orders.stream()
                .filter(order ->
                        order.status() == Status.WAITING && order.player().equals(player))
                .count();
        if (waiting >= MAX_WAITING_PER_PLAYER) {
            return Optional.empty();
        }
        Order order = new Order(nextId++, player, playerName, recipe, item, crafts, Status.WAITING, missing, "");
        orders.add(order);
        return Optional.of(order);
    }

    public boolean cancel(int id, String player) {
        return replace(
                id,
                order -> order.player().equals(player) && order.status() == Status.WAITING
                        ? Optional.of(order.with(Status.CANCELLED, Map.of(), ""))
                        : Optional.empty());
    }

    public boolean update(int id, Map<String, Integer> missing) {
        return replace(
                id,
                order -> order.status() == Status.WAITING
                        ? Optional.of(order.with(Status.WAITING, missing, ""))
                        : Optional.empty());
    }

    public boolean finish(int id, String where) {
        boolean done = replace(
                id,
                order -> order.status() == Status.WAITING
                        ? Optional.of(order.with(Status.DONE, Map.of(), where))
                        : Optional.empty());
        prune();
        return done;
    }

    private boolean replace(int id, Function<Order, Optional<Order>> change) {
        for (int index = 0; index < orders.size(); index++) {
            Order order = orders.get(index);
            if (order.id() == id) {
                Optional<Order> next = change.apply(order);
                if (next.isPresent()) {
                    orders.set(index, next.get());
                }
                return next.isPresent();
            }
        }
        return false;
    }

    private void prune() {
        long finished = orders.stream()
                .filter(order -> order.status() != Status.WAITING)
                .count();
        Iterator<Order> each = orders.iterator();
        while (each.hasNext() && finished > KEEP_FINISHED) {
            if (each.next().status() != Status.WAITING) {
                each.remove();
                finished--;
            }
        }
    }

    public List<Order> all() {
        return List.copyOf(orders);
    }

    public List<Order> waiting() {
        return orders.stream().filter(order -> order.status() == Status.WAITING).toList();
    }

    public int nextId() {
        return nextId;
    }

    public Map<String, Integer> shortage() {
        Map<String, Integer> total = new TreeMap<>();
        for (Order order : waiting()) {
            order.missing().forEach((item, count) -> total.merge(item, count, Integer::sum));
        }
        return Map.copyOf(total);
    }
}
