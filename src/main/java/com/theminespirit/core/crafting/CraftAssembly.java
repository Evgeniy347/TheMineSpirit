package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CraftAssembly {
    private static final int MAX_DEPTH = RecipeSpec.GRID_SLOTS + 8;

    public record Order(RecipeSpec recipe, List<String> items) {
        public Order {
            Objects.requireNonNull(recipe, "recipe не может быть null: шаг крафта без рецепта не выполнить");
            items = List.copyOf(items);
        }
    }

    private CraftAssembly() {}

    public static Optional<List<Order>> plan(
            String item, int count, Map<String, Integer> available, RecipeIndex index) {
        Objects.requireNonNull(item, "item не может быть null: без цели цепочку крафтов не построить");
        Objects.requireNonNull(
                available, "available не может быть null: без инвентаря промежуточные вещи не из чего делать");
        Objects.requireNonNull(index, "index не может быть null: рецепты берутся из индекса");
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество предметов должно быть больше 0, а получено " + count + ": крафтить ноль нечего");
        }
        State state = new State(index, new HashMap<>(available));
        return state.produce(item, count) ? Optional.of(List.copyOf(state.orders)) : Optional.empty();
    }

    private static final class State {
        private final RecipeIndex index;
        private final Map<String, Integer> stock;
        private final Set<String> path = new HashSet<>();
        private final List<Order> orders = new ArrayList<>();

        private State(RecipeIndex index, Map<String, Integer> stock) {
            this.index = index;
            this.stock = stock;
        }

        private boolean produce(String item, int count) {
            if (!path.add(item) || path.size() > MAX_DEPTH) {
                return false;
            }
            try {
                int need = count - stock.getOrDefault(item, 0);
                if (need <= 0) {
                    return true;
                }
                for (RecipeSpec recipe : candidates(item)) {
                    Snapshot before = snapshot();
                    int crafts = (need + recipe.outputCount() - 1) / recipe.outputCount();
                    if (craft(recipe, crafts)) {
                        return true;
                    }
                    restore(before);
                }
                return false;
            } finally {
                path.remove(item);
            }
        }

        private List<RecipeSpec> candidates(String item) {
            return index.craftsFor(item).stream()
                    .sorted(Comparator.comparing(RecipeSpec::id))
                    .toList();
        }

        private boolean craft(RecipeSpec recipe, int crafts) {
            for (int craft = 0; craft < crafts; craft++) {
                List<String> chosen = new ArrayList<>(recipe.ingredients().size());
                for (IngredientSpec slot : recipe.ingredients()) {
                    Optional<String> item = take(slot);
                    if (item.isEmpty()) {
                        return false;
                    }
                    chosen.add(item.get());
                }
                stock.merge(recipe.outputItem(), recipe.outputCount(), Integer::sum);
                orders.add(new Order(recipe, chosen));
            }
            return true;
        }

        private Optional<String> take(IngredientSpec slot) {
            for (String alternative : slot.alternatives()) {
                if (stock.getOrDefault(alternative, 0) > 0) {
                    consume(alternative);
                    return Optional.of(alternative);
                }
            }
            for (String alternative : slot.alternatives()) {
                Snapshot before = snapshot();
                if (produce(alternative, 1) && stock.getOrDefault(alternative, 0) > 0) {
                    consume(alternative);
                    return Optional.of(alternative);
                }
                restore(before);
            }
            return Optional.empty();
        }

        private void consume(String item) {
            stock.merge(item, -1, Integer::sum);
        }

        private Snapshot snapshot() {
            return new Snapshot(new HashMap<>(stock), orders.size());
        }

        private void restore(Snapshot snapshot) {
            stock.clear();
            stock.putAll(snapshot.stock());
            while (orders.size() > snapshot.orderCount()) {
                orders.remove(orders.size() - 1);
            }
        }
    }

    private record Snapshot(Map<String, Integer> stock, int orderCount) {}
}
