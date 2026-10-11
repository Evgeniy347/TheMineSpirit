package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
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

        assertEquals(new ScriptCall("gather", Map.of("block", "oak_log", "count", 5)), plan,
                "Gatherable предмет обязан резолвиться в скрипт gather с block и count: иначе дух не соберёт "
                        + "материал, доступный напрямую, и будет искать бесполезный рецепт");
    }

    @Test
    void resolve_SmeltableItem_ReturnsInputAndSmeltSequence() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 2, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                new ScriptCall("gather", Map.of("block", "iron_ore", "count", 2)),
                new ScriptCall("smelt", Map.of("item", "iron_ingot", "count", 2)))), plan,
                "Smeltable предмет обязан резолвиться в добычу входа и переплавку: без шага smelt дух получит "
                        + "руду вместо слитка и цель не будет исполнена");
    }

    @Test
    void resolve_CraftableItem_ReturnsIngredientsAndCraft() {
        TaskNode plan = ResourceResolver.resolve("stick", 1, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                new ScriptCall("gather", Map.of("block", "oak_log", "count", 2)),
                new ScriptCall("craft", Map.of("item", "stick", "count", 1)))), plan,
                "Craftable предмет обязан резолвиться в получение ингредиентов и скрипт craft: без рекурсивного "
                        + "разбора ингредиентов дух попробует скрафтить из ничего");
    }

    @Test
    void resolve_NestedCraftableItem_ResolvesIngredientsRecursively() {
        TaskNode plan = ResourceResolver.resolve("iron_pickaxe", 1, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                new SequenceNode(List.of(
                        new ScriptCall("gather", Map.of("block", "oak_log", "count", 4)),
                        new ScriptCall("craft", Map.of("item", "stick", "count", 2)))),
                new SequenceNode(List.of(
                        new ScriptCall("gather", Map.of("block", "iron_ore", "count", 3)),
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
                        new ScriptCall("gather", Map.of("block", "d", "count", 1)),
                        new ScriptCall("craft", Map.of("item", "b", "count", 1)))),
                new SequenceNode(List.of(
                        new ScriptCall("gather", Map.of("block", "d", "count", 1)),
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
        ResourceKnowledge knowledge = ResourceKnowledge.builder().gatherable("oak_log").build();
        assertThrows(PlanValidationException.class,
                () -> ResourceResolver.resolve("oak_log", 300, knowledge),
                "Резолвер обязан валидировать итоговый план по границам count: count > 256 должен отклоняться");
    }
}