package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NeedsOfPlanTests {
    private static final ResourceKnowledge KNOWLEDGE = new RecipeKnowledge();

    @Test
    void collect_CraftWithIngredients_ListsItemAndEverythingUnderIt() {
        TaskNode plan = new ScriptCall("craft", Map.of("item", "iron_pickaxe", "count", 1));

        assertEquals(
                List.of("iron_pickaxe", "iron_ingot", "stick"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "План крафта кирки защищает саму кирку, слитки и палки: ингредиенты нужны рецепту, "
                        + "и выброс слитка сорвал бы крафт на середине, иначе план остался бы без материала. "
                        + "При смене collect проверь крафт с ингредиентами");
    }

    @Test
    void collect_SequenceNode_WalksEveryStep() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("scan", Map.of()),
                new ScriptCall("gather", Map.of("block", "oak_log", "count", 4)),
                new ScriptCall("craft", Map.of("item", "oak_planks", "count", 4)),
                new ScriptCall("collect_drops", Map.of())));

        assertEquals(
                List.of("oak_log", "oak_planks"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Все шаги последовательности учтены: блок gather и предмет craft попадают в нужное, "
                        + "шаги без item и block (scan, collect_drops) вклад не вносят, иначе нужное потеряет шаг. "
                        + "При смене collect проверь sequence");
    }

    @Test
    void collect_BuildScripts_ListsBlockNeeds() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("place_block", Map.of("block", "cobblestone", "x", 0, "y", 64, "z", 0)),
                new ScriptCall("fill", Map.of("block", "dirt", "x1", 0, "y1", 64, "z1", 0, "x2", 1, "y2", 64, "z2", 1)),
                new ScriptCall("build_schematic", Map.of("file", "house", "x", 0, "y", 64, "z", 0))));

        assertEquals(
                List.of("cobblestone", "dirt"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Шаги стройки несут block и обязаны входить в нужное: иначе резервирование материалов не видит стройку "
                        + "и дух выбросит блоки до начала заливки. При смене collect проверь скрипты стройки");
    }

    @Test
    void collect_RepeatNode_WalksRepeatedStep() {
        TaskNode plan = new RepeatNode(4, new ScriptCall("craft", Map.of("item", "torch", "count", 4)));

        assertEquals(
                List.of("torch", "stick", "coal"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Шаг внутри repeat учтён: повторяющийся крафт защищает те же ингредиенты, "
                        + "иначе дух выбросил бы уголь между повторами. При смене collect проверь repeat");
    }

    @Test
    void collect_FallbackNode_WalksEveryBranch() {
        TaskNode plan = new FallbackNode(List.of(
                new ScriptCall("gather", Map.of("block", "iron_ore", "count", 1)),
                new ScriptCall("craft", Map.of("item", "bread", "count", 1))));

        assertEquals(
                List.of("iron_ore", "bread", "wheat"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                " Все ветки fallback входят в нужное: план может пойти по любой из них, "
                        + "и выбрасывать предметы ветки, которая ещё не началась, нельзя, иначе материал пропадёт. "
                        + "При смене collect проверь fallback");
    }

    @Test
    void collect_ParallelNode_WalksEveryBranch() {
        TaskNode plan = new ParallelNode(
                ParallelMode.ALL,
                List.of(
                        new ScriptCall("equip", Map.of("item", "iron_pickaxe")),
                        new ScriptCall("deposit", Map.of("item", "cobblestone", "count", 32))));

        assertEquals(
                List.of("iron_pickaxe", "iron_ingot", "stick", "cobblestone"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Параллельные ветки обе учитываются: подготовка кирки и вынос булыжника идут "
                        + "одновременно, значит и то и другое нужно плану, иначе выброс сорвёт ветку. "
                        + "При смене collect проверь parallel");
    }

    @Test
    void collect_ConditionNode_WalksBothBranches() {
        TaskNode plan = new ConditionNode(
                "has_item",
                Map.of("item", "bucket"),
                new ScriptCall("smelt", Map.of("item", "charcoal", "count", 1)),
                new ScriptCall("acquire", Map.of("item", "oak_log", "count", 1)));

        assertEquals(
                List.of("charcoal", "oak_log"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Обе ветки условия учтены: какая сработает — решит проверка, значит нужное собирают "
                        + "из then и else. Параметры самой проверки в нужное не входят: их читает "
                        + "ConditionExecutor, а не инвентарь, иначе защита придумает лишнее. "
                        + "При смене collect проверь condition");
    }

    @Test
    void collect_ConditionWithoutElse_WalksThenOnly() {
        TaskNode plan = new ConditionNode(
                "has_item",
                Map.of("item", "bucket"),
                new ScriptCall("smelt", Map.of("item", "charcoal", "count", 1)),
                null);

        assertEquals(
                List.of("charcoal"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Условие без else даёт только ветку then: ветки, которой нет в плане, и защищать "
                        + "нечем, иначе защита придумает лишнее. При смене collect проверь condition без else");
    }

    @Test
    void collect_CyclicRecipes_DoNotHang() {
        ResourceKnowledge cyclic = new RecipeKnowledge(
                Map.of("a", List.of(new Ingredient("b", 1)), "b", List.of(new Ingredient("a", 1))), Map.of(),
                Map.of(), Set.of());

        assertEquals(
                List.of("a", "b"),
                NeedsOfPlan.collect(new ScriptCall("craft", Map.of("item", "a", "count", 1)), cyclic),
                "Зацикленные рецепты не подвешивают сбор: предмет на собственном пути встречается "
                        + "второй раз и обрывает раскрытие, а не зависает, иначе тик поселения встанет. "
                        + "При смене collect проверь циклы");
    }

    @Test
    void collect_DeepRecipes_StopAtDepthLimit() {
        assertEquals(
                List.of(
                        "ladder_0",
                        "ladder_1",
                        "ladder_2",
                        "ladder_3",
                        "ladder_4",
                        "ladder_5",
                        "ladder_6",
                        "ladder_7",
                        "ladder_8"),
                NeedsOfPlan.collect(new ScriptCall("craft", Map.of("item", "ladder_0", "count", 1)), KNOWLEDGE),
                "Раскрытие ингредиентов идёт на глубину " + NeedsOfPlan.MAX_DEPTH
                        + ": дальше цепочка рецептов не влияет на защиту, и обход заканчивается, "
                        + "иначе глубокий крафт подвесил бы план. При смене collect проверь предельную глубину");
    }

    @Test
    void collect_SameItemInSeveralSteps_ListedOnce() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("gather", Map.of("block", "oak_log", "count", 1)),
                new ScriptCall("gather", Map.of("block", "oak_log", "count", 1)),
                new ScriptCall("craft", Map.of("item", "oak_planks", "count", 4))));

        assertEquals(
                List.of("oak_log", "oak_planks"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Повторяющийся предмет называется один раз: список нужного идёт в защиту, "
                        + "а дубли шаблонов только запутали бы отчёт, иначе защита раздуется. "
                        + "При смене collect проверь повтор предмета");
    }

    @Test
    void collect_UnknownScriptWithItemParam_IsIgnored() {
        TaskNode plan = new ScriptCall("wait", Map.of("item", "diamond", "ticks", 20));

        assertEquals(
                List.of(),
                NeedsOfPlan.collect(plan, KnowledgeFor()),
                "Скрипт вне списка item/craft/smelt/equip/deposit/acquire/gather не защищает свой item: "
                        + "иначе параметр item у любого нового скрипта молча делал предмет неуничтожимым. "
                        + "При смене collect проверь незнакомый скрипт");
    }

    @ParameterizedTest(name = "план из {0} даёт {1}")
    @CsvSource({
        "craft, iron_pickaxe",
        "smelt, iron_ingot",
        "equip, iron_pickaxe",
        "deposit, cobblestone",
        "acquire, diamond"
    })
    void collect_ItemScripts_ListTheirItem(String script, String item) {
        TaskNode plan = new ScriptCall(script, Map.of("item", item, "count", 1));

        assertTrue(
                NeedsOfPlan.collect(plan, KnowledgeFor()).contains(item),
                "Скрипт '" + script + "' защищает свой параметр item: предмет заказа плана не должен "
                        + "выбрасываться при переполнении инвентаря, иначе предмет заказа потеряется. "
                        + "При смене collect проверь скрипт '" + script + "'");
    }

    @Test
    void collect_ItemScripts_ListOnlyTheirItem() {
        TaskNode plan = new ScriptCall("smelt", Map.of("item", "iron_ingot", "count", 1));

        assertEquals(
                List.of("iron_ingot"),
                NeedsOfPlan.collect(plan, KNOWLEDGE),
                "Плавка защищает свой продукт, а сырьё ей приносит шаг acquire или gather того же плана: "
                        + "защита раскрывает только рецепты ingredientsFor, поэтому дубль сырья здесь "
                        + "разошёлся бы с тем, что считает ResourceResolver, иначе защита разойдётся с резервированием. "
                        + "При смене collect проверь плавку");
    }

    private static ResourceKnowledge KnowledgeFor() {
        return new RecipeKnowledge(Map.of(), Map.of(), Map.of(), Set.of());
    }
}
