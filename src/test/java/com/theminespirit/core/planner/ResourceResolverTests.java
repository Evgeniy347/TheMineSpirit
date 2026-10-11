package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceResolverTests {
    private static final ResourceKnowledge KNOWLEDGE = new StubKnowledge(
            Set.of("oak_log", "iron_ore"),
            Map.of(
                    "stick", List.of(new Ingredient("oak_log", 2)),
                    "iron_pickaxe", List.of(new Ingredient("stick", 2), new Ingredient("iron_ingot", 3))),
            Map.of("iron_ingot", "iron_ore"));

    @Test
    void resolve_GatherableItem_ReturnsGatherCall() {
        TaskNode plan = ResourceResolver.resolve("oak_log", 5, KNOWLEDGE);

        assertEquals(gather("oak_log", 5), plan,
                "Gatherable предмет обязан резолвиться в скрипт gather с block и count: иначе дух не соберёт "
                        + "материал, доступный напрямую, и будет искать бесполезный рецепт");
    }

    @Test
    void resolve_SmeltableItem_ReturnsInputAndSmeltSequence() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 2, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 2),
                new ScriptCall("smelt", Map.of("item", "iron_ingot", "count", 2)))), plan,
                "Smeltable предмет обязан резолвиться в добычу входа и переплавку: без шага smelt дух получит "
                        + "руду вместо слитка и цель не будет исполнена");
    }

    @Test
    void resolve_CraftableItem_ReturnsIngredientsAndCraft() {
        TaskNode plan = ResourceResolver.resolve("stick", 1, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                gather("oak_log", 2),
                new ScriptCall("craft", Map.of("item", "stick", "count", 1)))), plan,
                "Craftable предмет обязан резолвиться в получение ингредиентов и скрипт craft: без рекурсивного "
                        + "разбора ингредиентов дух попробует скрафтить из ничего");
    }

    @Test
    void resolve_CraftableWithOutputCount_ResolvesIngredientsForCraftsNotItems() {
        ResourceKnowledge planks = new StubKnowledge(
                Set.of("oak_log"),
                Map.of("oak_planks", List.of(new Ingredient("oak_log", 1))),
                Map.of(),
                Map.of("oak_planks", 4));

        TaskNode plan = ResourceResolver.resolve("oak_planks", 8, planks);

        assertEquals(new SequenceNode(List.of(
                gather("oak_log", 2),
                new ScriptCall("craft", Map.of("item", "oak_planks", "count", 8)))), plan,
                "Рецепт с выходом 4 предмета обязан требовать 2 крафта на 8 досок: иначе резолвер добывает "
                        + "8 брёвен вместо 2 и заваливает духа лишним материалом");
    }

    @Test
    void resolve_SingleItemFromMultiOutputRecipe_RoundsCraftsUp() {
        ResourceKnowledge planks = new StubKnowledge(
                Set.of("oak_log"),
                Map.of("oak_planks", List.of(new Ingredient("oak_log", 1))),
                Map.of(),
                Map.of("oak_planks", 4));

        TaskNode plan = ResourceResolver.resolve("oak_planks", 1, planks);

        assertEquals(new SequenceNode(List.of(
                gather("oak_log", 1),
                new ScriptCall("craft", Map.of("item", "oak_planks", "count", 1)))), plan,
                "Одна доска из рецепта на 4 не может дать полкрафта: число крафтов округляется вверх до 1, "
                        + "иначе план добывает 0 брёвен и крафт провалится");
    }

    @Test
    void resolve_IngredientCountOverflow_ThrowsUnplannableGoal() {
        ResourceKnowledge heavy = new StubKnowledge(
                Set.of("oak_log"),
                Map.of("heavy", List.of(new Ingredient("oak_log", 2))),
                Map.of());

        UnplannableGoalException error = assertThrows(UnplannableGoalException.class,
                () -> ResourceResolver.resolve("heavy", Integer.MAX_VALUE, heavy),
                "Переполнение произведения количества ингредиента на число крафтов обязано отвергаться: "
                        + "тихое переполнение int превратит план в отрицательную добычу");

        assertEquals("heavy", error.item(),
                "Исключение о переполнении обязано нести предмет 'heavy' отдельным полем: по нему правят цель");
    }

    @Test
    void resolve_NestedCraftableItem_ResolvesIngredientsRecursively() {
        TaskNode plan = ResourceResolver.resolve("iron_pickaxe", 1, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                new SequenceNode(List.of(
                        gather("oak_log", 4),
                        new ScriptCall("craft", Map.of("item", "stick", "count", 2)))),
                new SequenceNode(List.of(
                        gather("iron_ore", 3),
                        new ScriptCall("smelt", Map.of("item", "iron_ingot", "count", 3)))),
                new ScriptCall("craft", Map.of("item", "iron_pickaxe", "count", 1)))), plan,
                "Вложенный craft обязан рекурсивно резолвить каждый ингредиент с учётом количества: иначе план "
                        + "пропустит добычу промежуточного предмета и кирка не скрафтится");
    }

    @Test
    void resolve_SharedIngredientReusedInTwoBranches_DoesNotTriggerFalseCycle() {
        ResourceKnowledge shared = new StubKnowledge(
                Set.of("d"),
                Map.of(
                        "a", List.of(new Ingredient("b", 1), new Ingredient("c", 1)),
                        "b", List.of(new Ingredient("d", 1)),
                        "c", List.of(new Ingredient("d", 1))),
                Map.of());

        TaskNode plan = ResourceResolver.resolve("a", 1, shared);

        assertEquals(new SequenceNode(List.of(
                new SequenceNode(List.of(
                        gather("d", 1),
                        new ScriptCall("craft", Map.of("item", "b", "count", 1)))),
                new SequenceNode(List.of(
                        gather("d", 1),
                        new ScriptCall("craft", Map.of("item", "c", "count", 1)))),
                new ScriptCall("craft", Map.of("item", "a", "count", 1)))), plan,
                "Общий ингредиент в двух ветках — не цикл: он обязан резолвиться дважды. Если путь рекурсии "
                        + "не освобождается после ветки, цепочка прервётся ложным UnplannableGoalException");
    }

    @Test
    void resolve_UnknownItem_ThrowsUnplannableGoalNamingItem() {
        UnplannableGoalException error = assertThrows(UnplannableGoalException.class,
                () -> ResourceResolver.resolve("diamond", 1, KNOWLEDGE),
                "Предмет без способа добычи обязан давать UnplannableGoalException: молчаливый пропуск предмета "
                        + "превратит цель в план, который ничего не добывает");

        assertTrue(error.getMessage().contains("diamond"),
                "Сообщение обязано называть недостижимый предмет 'diamond': по нему правят знания. "
                        + "Фактическое: " + error.getMessage());
        assertEquals("diamond", error.item(),
                "Исключение обязано нести предмет 'diamond' отдельным полем: разбор кавычек из текста ломается "
                        + "от любой правки формулировки");
    }

    @Test
    void resolve_CyclicRecipe_ThrowsUnplannableGoal() {
        ResourceKnowledge cyclic = new StubKnowledge(
                Set.of(),
                Map.of(
                        "a", List.of(new Ingredient("b", 1)),
                        "b", List.of(new Ingredient("a", 1))),
                Map.of());

        UnplannableGoalException error = assertThrows(UnplannableGoalException.class,
                () -> ResourceResolver.resolve("a", 1, cyclic),
                "Зацикленный рецепт обязан прерываться: иначе рекурсия заполнит стек и уронит серверный тик");

        assertTrue(error.getMessage().contains("a"),
                "Сообщение о цикле обязано называть предмет 'a', на котором замкнулась цепочка. "
                        + "Фактическое: " + error.getMessage());
        assertEquals("a", error.item(),
                "Исключение о цикле обязано нести предмет 'a' отдельным полем: цикл привязан к конкретному предмету");
    }

    @Test
    void resolve_CountZero_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ResourceResolver.resolve("oak_log", 0, KNOWLEDGE),
                "Количество 0 обязано отвергаться: скрипт gather со count=0 молча ничего не делает, "
                        + "а цель выглядит исполненной");
    }

    @Test
    void resolve_CountNegative_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ResourceResolver.resolve("oak_log", -1, KNOWLEDGE),
                "Отрицательное количество обязано отвергаться: отрицательный count через умножение ингредиентов "
                        + "превратит план в бессмысленные отрицательные добычи");
    }

    @Test
    void ingredient_CountZero_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("oak_log", 0),
                "Ингредиент с count=0 обязан отвергаться: нулевое количество в рецепте даёт план с мёртвым шагом");
    }

    @Test
    void ingredient_BlankItem_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient(" ", 1),
                "Ингредиент с пустым именем обязан отвергаться: без имени резолвер не найдёт предмет, "
                        + "и ошибка всплывёт далеко от места создания ингредиента");
    }

    @Test
    void resolve_CountOutOfRange_ThrowsValidationException() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of("oak_log"), Map.of(), Map.of());
        assertThrows(PlanValidationException.class,
                () -> ResourceResolver.resolve("oak_log", 300, knowledge),
                "Резолвер обязан валидировать итоговый план по границам count: count > 256 должен отклоняться");
    }

    private static ScriptCall gather(String block, int count) {
        return new ScriptCall("gather", Map.of("block", block, "count", count,
                "radius", CatalogLimits.HARVEST_RADIUS_DEFAULT));
    }
}