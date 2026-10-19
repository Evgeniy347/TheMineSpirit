package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class NeedsOfPlanProperties {
    private static final Set<String> ITEM_SCRIPTS = Set.of("craft", "smelt", "equip", "deposit", "acquire");
    private static final int TREE_DEPTH = 3;

    @Provide
    Arbitrary<TaskNode> plans() {
        return node(calls(), TREE_DEPTH);
    }

    private static Arbitrary<TaskNode> node(Arbitrary<TaskNode> call, int depth) {
        if (depth == 0) {
            return call;
        }
        Arbitrary<TaskNode> deeper = node(call, depth - 1);
        return Arbitraries.oneOf(
                call,
                Combinators.combine(Arbitraries.integers().between(0, 4), deeper)
                        .as((times, step) -> new RepeatNode(times, step)),
                deeper.list().ofMaxSize(3).map(SequenceNode::new),
                deeper.list().ofMaxSize(2).map(FallbackNode::new),
                Combinators.combine(
                                Arbitraries.of(ParallelMode.values()),
                                deeper.list().ofMaxSize(2))
                        .as(ParallelNode::new),
                Combinators.combine(deeper, deeper)
                        .as((thenStep, elseStep) ->
                                new ConditionNode("has_item", Map.of("item", "bucket"), thenStep, elseStep)));
    }

    private static Arbitrary<TaskNode> calls() {
        Arbitrary<String> scripts = Arbitraries.of(
                "craft", "smelt", "equip", "deposit", "acquire", "gather", "scan", "wait", "collect_drops");
        Arbitrary<String> items = Arbitraries.of(
                "iron_pickaxe",
                "oak_log",
                "bread",
                "torch",
                "cobblestone",
                "iron_ingot",
                "raw_iron",
                "stick",
                "coal",
                "diamond");
        return Combinators.combine(scripts, items).as((script, item) -> (TaskNode)
                ("gather".equals(script)
                        ? new ScriptCall(script, Map.of("block", item, "count", 1))
                        : new ScriptCall(script, Map.of("item", item, "count", 1))));
    }

    @Property
    void collect_AnyPlan_ListsItemsOfItsScripts(@ForAll("plans") TaskNode plan) {
        List<String> needs = NeedsOfPlan.collect(plan, NoRecipes.INSTANCE);

        for (String item : itemParamsOf(plan)) {
            assertTrue(
                    needs.contains(item),
                    "Предмет '" + item + "' заказан скриптом плана и обязан быть в нужном: иначе дух "
                            + "выбросил бы его при переполнении инвентаря и сорвал собственный план. "
                            + "Нужное " + needs + ", план " + plan);
        }
    }

    @Property
    void collect_PlanWithRecipes_ListsItemsEvenWhenIngredientsExist(@ForAll("plans") TaskNode plan) {
        List<String> needs = NeedsOfPlan.collect(plan, WithRecipes.INSTANCE);

        for (String item : itemParamsOf(plan)) {
            assertTrue(
                    needs.contains(item),
                    "Предмет '" + item + "' заказан скриптом плана и защищён, даже когда у него "
                            + "раскрыты ингредиенты: защита покрывает и продукт, и сырьё. Нужное " + needs
                            + ", план " + plan);
        }
    }

    @Property
    void collect_CyclicRecipes_FinishAndStayShort(@ForAll("plans") TaskNode plan) {
        List<String> needs = NeedsOfPlan.collect(plan, CyclicRecipes.INSTANCE);

        assertTrue(
                needs.size() <= 64,
                "Сбор нужного обязан заканчиваться и оставаться коротким: получилось " + needs.size()
                        + " шаблонов. План " + plan);
    }

    private static List<String> itemParamsOf(TaskNode node) {
        List<String> items = new ArrayList<>();
        collectItems(node, items);
        return items;
    }

    private static void collectItems(TaskNode node, List<String> items) {
        switch (node) {
            case ScriptCall call -> {
                if (ITEM_SCRIPTS.contains(call.script()) && call.params().get("item") instanceof String text) {
                    items.add(text);
                }
            }
            case SequenceNode sequence -> sequence.steps().forEach(step -> collectItems(step, items));
            case FallbackNode fallback -> fallback.steps().forEach(step -> collectItems(step, items));
            case ParallelNode parallel -> parallel.steps().forEach(step -> collectItems(step, items));
            case RepeatNode repeat -> collectItems(repeat.step(), items);
            case ConditionNode condition -> {
                collectItems(condition.thenStep(), items);
                if (condition.elseStep() != null) {
                    collectItems(condition.elseStep(), items);
                }
            }
        }
    }

    private static class NoRecipes implements ResourceKnowledge {
        private static final NoRecipes INSTANCE = new NoRecipes();

        @Override
        public boolean isGatherable(String item) {
            return false;
        }

        @Override
        public boolean isCraftable(String item) {
            return false;
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public boolean isSmeltable(String item) {
            return false;
        }

        @Override
        public String smeltInputFor(String item) {
            return null;
        }
    }

    private static final class WithRecipes extends NoRecipes {
        private static final WithRecipes INSTANCE = new WithRecipes();
        private static final Map<String, List<Ingredient>> RECIPES = Map.of(
                "iron_pickaxe", List.of(new Ingredient("iron_ingot", 3), new Ingredient("stick", 2)),
                "bread", List.of(new Ingredient("wheat", 3)),
                "torch", List.of(new Ingredient("stick", 1), new Ingredient("coal", 1)),
                "iron_ingot", List.of(new Ingredient("raw_iron", 1)));

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return RECIPES.getOrDefault(item, List.of());
        }
    }

    private static final class CyclicRecipes extends NoRecipes {
        private static final CyclicRecipes INSTANCE = new CyclicRecipes();
        private static final Map<String, List<Ingredient>> RECIPES = Map.of(
                "a", List.of(new Ingredient("b", 1)),
                "b", List.of(new Ingredient("a", 1)),
                "c", List.of(new Ingredient("d", 1), new Ingredient("e", 1)),
                "d", List.of(new Ingredient("c", 1)),
                "e", List.of(new Ingredient("a", 1)));

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return RECIPES.getOrDefault(item, List.of());
        }
    }
}
