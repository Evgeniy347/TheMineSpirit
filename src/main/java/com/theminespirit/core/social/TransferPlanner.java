package com.theminespirit.core.social;

import com.theminespirit.core.inventory.InventoryNorm;
import com.theminespirit.core.inventory.Slice;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.Ingredient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TransferPlanner {
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator.comparingInt(Candidate::surplus)
            .reversed()
            .thenComparingDouble(Candidate::distance)
            .thenComparing(candidate -> candidate.source().spirit());

    private TransferPlanner() {}

    public static List<TransferOrder> plan(
            List<TransferNeed> needs,
            List<TransferSource> sources,
            Map<String, Integer> warehouse,
            ValueTable values,
            int radius,
            int maxCount) {
        Objects.requireNonNull(needs, "Нужды передачи не могут быть null: пустой список задают пустым");
        Objects.requireNonNull(sources, "Источники не могут быть null: пустой список задают пустым");
        Objects.requireNonNull(warehouse, "Склад не может быть null: пустой склад задают пустой картой");
        Objects.requireNonNull(values, "Ценности не могут быть null: без них норма не выбирает лучшее");
        if (radius < 0) {
            throw new IllegalArgumentException(
                    "Радиус передачи " + radius + " отрицателен: близость " + "считается расстоянием");
        }
        if (maxCount <= 0) {
            throw new IllegalArgumentException(
                    "Предел передачи " + maxCount + " не положителен: " + "пустая передача не создаёт задачи");
        }
        List<TransferNeed> orderedNeeds = new ArrayList<>(needs);
        orderedNeeds.sort(Comparator.comparing(TransferNeed::recipient).thenComparing(TransferNeed::item));
        List<TransferSource> orderedSources = new ArrayList<>(sources);
        orderedSources.sort(Comparator.comparing(TransferSource::spirit));
        Map<UUID, Map<String, Integer>> allocated = new HashMap<>();
        List<TransferOrder> orders = new ArrayList<>();
        for (TransferNeed need : orderedNeeds) {
            int remaining = need.count();
            List<Candidate> candidates = candidates(need, orderedSources, values, radius, allocated);
            for (Candidate candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                int count = Math.min(remaining, Math.min(candidate.surplus(), maxCount));
                if (count <= 0) {
                    continue;
                }
                orders.add(new TransferOrder(
                        Optional.of(candidate.source().spirit()),
                        need.recipient(),
                        need.item(),
                        count,
                        candidate.distance()));
                allocated
                        .computeIfAbsent(candidate.source().spirit(), ignored -> new HashMap<>())
                        .merge(need.item(), count, Integer::sum);
                remaining -= count;
            }
            if (remaining > 0) {
                int stored = warehouse.getOrDefault(need.item(), 0);
                int count = Math.min(remaining, Math.min(stored, maxCount));
                if (count > 0) {
                    orders.add(new TransferOrder(Optional.empty(), need.recipient(), need.item(), count, 0));
                }
            }
        }
        return List.copyOf(orders);
    }

    public static List<TransferOrder> planForCraft(CraftRequest request) {
        String craftItem = request.craftItem();
        UUID crafter = request.crafter();
        BlockCoord at = request.at();
        List<Stack> inventory = request.inventory();
        List<Ingredient> ingredients = request.ingredients();
        List<TransferSource> sources = request.sources();
        Map<String, Integer> warehouse = request.warehouse();
        ValueTable values = request.values();
        int radius = request.radius();
        int maxCount = request.maxCount();
        if (craftItem == null || craftItem.isBlank()) {
            throw new IllegalArgumentException(
                    "Предмет крафта пуст: без него неясно, чьи ингредиенты " + "собирает передача");
        }
        Objects.requireNonNull(crafter, "Крафтер не может быть null: передача адресована ему");
        Objects.requireNonNull(at, "Точка крафтера не может быть null: по ней ищут ближайший излишек");
        Objects.requireNonNull(inventory, "Инвентарь крафтера не может быть null: нехватка считается по нему");
        Objects.requireNonNull(
                ingredients, "Ингредиенты рецепта не могут быть null: пустой рецепт " + "задают пустым списком");
        Map<String, Integer> have = new HashMap<>();
        for (Stack stack : inventory) {
            if (stack != null) {
                have.merge(stack.item(), stack.count(), Integer::sum);
            }
        }
        List<TransferNeed> needs = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            int missing = ingredient.count() - have.getOrDefault(ingredient.item(), 0);
            if (missing > 0) {
                needs.add(new TransferNeed(crafter, ingredient.item(), missing, at));
            }
        }
        return plan(needs, sources, warehouse, values, radius, maxCount);
    }

    private static List<Candidate> candidates(
            TransferNeed need,
            List<TransferSource> sources,
            ValueTable values,
            int radius,
            Map<UUID, Map<String, Integer>> allocated) {
        List<Candidate> candidates = new ArrayList<>();
        for (TransferSource source : sources) {
            if (source.spirit().equals(need.recipient())) {
                continue;
            }
            double distance = distance(source.at(), need.at());
            if (distance > radius) {
                continue;
            }
            int surplus = surplus(source, need.item(), values)
                    - allocated.getOrDefault(source.spirit(), Map.of()).getOrDefault(need.item(), 0);
            if (surplus > 0) {
                candidates.add(new Candidate(source, surplus, distance));
            }
        }
        candidates.sort(CANDIDATE_ORDER);
        return candidates;
    }

    private static int surplus(TransferSource source, String item, ValueTable values) {
        int total = 0;
        for (Slice slice : InventoryNorm.extra(source.inventory(), source.kit(), source.taskNeeds(), values)) {
            Stack stack = source.inventory().get(slice.slot());
            if (stack != null && stack.item().equals(item)) {
                total += slice.count();
            }
        }
        return total;
    }

    private static double distance(BlockCoord from, BlockCoord to) {
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        double dz = from.z() - to.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record Candidate(TransferSource source, int surplus, double distance) {}

    public record CraftRequest(
            String craftItem,
            UUID crafter,
            BlockCoord at,
            List<Stack> inventory,
            List<Ingredient> ingredients,
            List<TransferSource> sources,
            Map<String, Integer> warehouse,
            ValueTable values,
            int radius,
            int maxCount) {}
}
