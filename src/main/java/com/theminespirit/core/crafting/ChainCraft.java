package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class ChainCraft {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_PROBES = 4096;

    private final RecipeIndex index;
    private final boolean onCredit;
    private int probes;

    private ChainCraft(RecipeIndex index, boolean onCredit) {
        this.index = index;
        this.onCredit = onCredit;
    }

    public static ChainPlan ofRecipe(RecipeIndex index, RecipeSpec recipe, int crafts, Map<String, Integer> stock) {
        Objects.requireNonNull(index, "Индекс рецептов не может быть null: по нему ищутся промежуточные");
        Objects.requireNonNull(recipe, "Целевой рецепт не может быть null");
        Objects.requireNonNull(stock, "Наличие не может быть null: пустое задают пустой картой");
        if (recipe.kind() == RecipeKind.SMELTING) {
            throw new IllegalArgumentException("Рецепт '" + recipe.id() + "' — плавка, а цепочка крафта собирает "
                    + "только рецепты верстака: печь требует времени и топлива");
        }
        if (crafts <= 0) {
            throw new IllegalArgumentException("Число крафтов должно быть больше 0, а получено " + crafts);
        }
        Map<String, Integer> initial = positive(stock);
        Ledger strict = new Ledger(initial);
        Set<String> path = Set.of(recipe.outputItem());
        if (new ChainCraft(index, false).craft(strict, recipe, crafts, path, 0)) {
            return ChainPlan.of(recipe, crafts, initial, strict.stock, strict.steps, Map.of());
        }
        Ledger credit = new Ledger(initial);
        if (!new ChainCraft(index, true).craft(credit, recipe, crafts, path, 0)) {
            return ChainPlan.of(recipe, crafts, initial, initial, List.of(), firstChoices(recipe, crafts));
        }
        return ChainPlan.of(recipe, crafts, initial, credit.stock, credit.steps, credit.missing);
    }

    public static Optional<ChainPlan> ofItem(RecipeIndex index, String item, int count, Map<String, Integer> stock) {
        Objects.requireNonNull(index, "Индекс рецептов не может быть null: по нему ищется рецепт цели");
        Objects.requireNonNull(item, "Целевой предмет не может быть null");
        if (count <= 0) {
            throw new IllegalArgumentException("Количество цели должно быть больше 0, а получено " + count);
        }
        Optional<ChainPlan> fallback = Optional.empty();
        for (RecipeSpec recipe : mostStockedFirst(index.craftsFor(item), stock)) {
            int crafts = ceilDiv(count, recipe.outputCount());
            ChainPlan plan = ofRecipe(index, recipe, crafts, stock);
            if (plan.feasible()) {
                return Optional.of(plan);
            }
            if (fallback.isEmpty()) {
                fallback = Optional.of(plan);
            }
        }
        return fallback;
    }

    public static int maxCrafts(RecipeIndex index, RecipeSpec recipe, Map<String, Integer> stock, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Предел крафтов должен быть больше 0, а получено " + limit);
        }
        int low = 0;
        int high = limit;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (ofRecipe(index, recipe, middle, stock).feasible()) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    static List<RecipeSpec> mostStockedFirst(List<RecipeSpec> recipes, Map<String, Integer> stock) {
        Comparator<RecipeSpec> byStock = Comparator.comparingLong((RecipeSpec recipe) -> stockedScore(recipe, stock))
                .reversed();
        return recipes.stream()
                .filter(recipe -> recipe.kind() != RecipeKind.SMELTING)
                .sorted(byStock.thenComparing(RecipeSpec::id))
                .toList();
    }

    private static long stockedScore(RecipeSpec recipe, Map<String, Integer> stock) {
        long score = 0;
        for (IngredientSpec slot : recipe.ingredients()) {
            int best = 0;
            for (String alternative : slot.alternatives()) {
                best = Math.max(best, stock.getOrDefault(alternative, 0));
            }
            score += best;
        }
        return score;
    }

    private boolean craft(Ledger ledger, RecipeSpec recipe, int crafts, Set<String> path, int depth) {
        probes++;
        List<IngredientSpec> slots = repeated(recipe.ingredients(), crafts);
        for (int attempt = 0; attempt <= slots.size(); attempt++) {
            Optional<List<String>> assignment = IngredientAssigner.assign(slots, ledger.stock);
            if (assignment.isPresent()) {
                for (String item : assignment.get()) {
                    ledger.add(item, -1);
                }
                ledger.add(recipe.outputItem(), crafts * recipe.outputCount());
                ledger.steps.add(new ChainStep(recipe, crafts, assignment.get()));
                return true;
            }
            for (Map.Entry<IngredientSpec, Integer> deficit :
                    deficits(slots, ledger.stock).entrySet()) {
                if (!supply(ledger, deficit.getKey(), deficit.getValue(), path, depth + 1)) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean supply(Ledger ledger, IngredientSpec slot, int count, Set<String> path, int depth) {
        List<String> order = cheapestAlternativesFirst(slot, ledger.stock);
        if (depth <= MAX_DEPTH && probes <= MAX_PROBES) {
            for (String alternative : order) {
                if (path.contains(alternative)) {
                    continue;
                }
                if (onCredit
                        && mostStockedFirst(index.craftsFor(alternative), ledger.stock)
                                .isEmpty()) {
                    borrow(ledger, alternative, count);
                    return true;
                }
                Ledger trial = ledger.copy();
                if (produce(trial, alternative, count, path, depth)) {
                    ledger.adopt(trial);
                    return true;
                }
            }
        }
        if (!onCredit) {
            return false;
        }
        Optional<String> outsidePath = order.stream()
                .filter(alternative -> !path.contains(alternative))
                .findFirst();
        if (outsidePath.isEmpty()) {
            return false;
        }
        borrow(ledger, outsidePath.get(), count);
        return true;
    }

    private static void borrow(Ledger ledger, String item, int count) {
        ledger.add(item, count);
        ledger.missing.merge(item, count, Integer::sum);
    }

    private boolean produce(Ledger ledger, String item, int count, Set<String> path, int depth) {
        Set<String> next = new HashSet<>(path);
        next.add(item);
        for (RecipeSpec recipe : mostStockedFirst(index.craftsFor(item), ledger.stock)) {
            Ledger trial = ledger.copy();
            if (craft(trial, recipe, ceilDiv(count, recipe.outputCount()), next, depth)) {
                ledger.adopt(trial);
                return true;
            }
        }
        return false;
    }

    private List<String> cheapestAlternativesFirst(IngredientSpec slot, Map<String, Integer> stock) {
        Map<String, Long> scores = new HashMap<>();
        for (String alternative : slot.alternatives()) {
            long stocked = 0;
            boolean craftable = false;
            for (RecipeSpec recipe : index.craftsFor(alternative)) {
                if (recipe.kind() != RecipeKind.SMELTING) {
                    craftable = true;
                    stocked = Math.max(stocked, stockedScore(recipe, stock));
                }
            }
            scores.put(alternative, stockedFirstThenRaw(stocked, craftable));
        }
        List<String> order = new ArrayList<>(slot.alternatives());
        order.sort(Comparator.comparingLong((String alternative) -> scores.getOrDefault(alternative, -1L))
                .reversed());
        return order;
    }

    private static long stockedFirstThenRaw(long stocked, boolean craftable) {
        if (stocked > 0) {
            return stocked + 1;
        }
        return craftable ? 0 : 1;
    }

    private static Map<IngredientSpec, Integer> deficits(List<IngredientSpec> slots, Map<String, Integer> stock) {
        Map<String, Integer> left = new HashMap<>(stock);
        Map<IngredientSpec, Integer> deficits = new LinkedHashMap<>();
        for (IngredientSpec slot : slots) {
            String best = slot.alternatives().get(0);
            for (String alternative : slot.alternatives()) {
                if (left.getOrDefault(alternative, 0) > left.getOrDefault(best, 0)) {
                    best = alternative;
                }
            }
            if (left.getOrDefault(best, 0) > 0) {
                left.merge(best, -1, Integer::sum);
            } else {
                deficits.merge(slot, 1, Integer::sum);
            }
        }
        return deficits;
    }

    private static Map<String, Integer> firstChoices(RecipeSpec recipe, int crafts) {
        Map<String, Integer> wanted = new TreeMap<>();
        for (IngredientSpec slot : recipe.ingredients()) {
            wanted.merge(slot.alternatives().get(0), crafts, Integer::sum);
        }
        return wanted;
    }

    private static List<IngredientSpec> repeated(List<IngredientSpec> ingredients, int times) {
        List<IngredientSpec> slots = new ArrayList<>(ingredients.size() * times);
        for (int i = 0; i < times; i++) {
            slots.addAll(ingredients);
        }
        return slots;
    }

    private static Map<String, Integer> positive(Map<String, Integer> stock) {
        Map<String, Integer> copy = new HashMap<>();
        stock.forEach((item, count) -> {
            if (count > 0) {
                copy.put(item, count);
            }
        });
        return Map.copyOf(copy);
    }

    private static int ceilDiv(int count, int per) {
        return (int) (((long) count + per - 1) / per);
    }

    private static final class Ledger {
        private final Map<String, Integer> stock;
        private final List<ChainStep> steps;
        private final Map<String, Integer> missing;

        private Ledger(Map<String, Integer> initial) {
            this(new HashMap<>(initial), new ArrayList<>(), new TreeMap<>());
        }

        private Ledger(Map<String, Integer> stock, List<ChainStep> steps, Map<String, Integer> missing) {
            this.stock = stock;
            this.steps = steps;
            this.missing = missing;
        }

        private Ledger copy() {
            return new Ledger(new HashMap<>(stock), new ArrayList<>(steps), new TreeMap<>(missing));
        }

        private void adopt(Ledger other) {
            stock.clear();
            stock.putAll(other.stock);
            steps.clear();
            steps.addAll(other.steps);
            missing.clear();
            missing.putAll(other.missing);
        }

        private void add(String item, int count) {
            int next = stock.getOrDefault(item, 0) + count;
            if (next > 0) {
                stock.put(item, next);
            } else {
                stock.remove(item);
            }
        }
    }
}
