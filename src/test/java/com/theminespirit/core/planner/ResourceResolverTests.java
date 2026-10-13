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
    private static final int STATION_RADIUS = 16;

    private static final ResourceKnowledge KNOWLEDGE = new StubKnowledge(
            Set.of("oak_log", "iron_ore", "coal"),
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
    void resolve_DropWithSourceBlock_GatherCallNamesSourceBlock() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of("raw_iron"), Map.of(), Map.of())
                .withGatherSource("raw_iron", "iron_ore");

        TaskNode plan = ResourceResolver.resolve("raw_iron", 3, knowledge);

        assertEquals(gather("iron_ore", 3), plan,
                "Дроп обязан резолвиться в сбор блока-источника: raw_iron не является блоком, gather block=raw_iron "
                        + "не нашёл бы ни одного блока в мире и добыча завершилась бы провалом");
    }

    @Test
    void resolve_SmeltableItem_ReturnsInputFuelAndSmeltSequence() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 2, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 2),
                gather("coal", 1),
                smelt("iron_ingot", 2))), plan,
                "Smeltable предмет обязан резолвиться в добычу входа, добычу топлива и переплавку: без шага топлива "
                        + "дух положит руду в холодную печь и цель не будет исполнена. 2 слитка углём (8 за штуку) "
                        + "требуют 1 угля");
    }

    @Test
    void resolve_CraftableItem_ReturnsIngredientsAndCraft() {
        TaskNode plan = ResourceResolver.resolve("stick", 1, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                gather("oak_log", 2),
                craft("stick", 1))), plan,
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
                craft("oak_planks", 8))), plan,
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
                craft("oak_planks", 1))), plan,
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
                        craft("stick", 2))),
                new SequenceNode(List.of(
                        gather("iron_ore", 3),
                        gather("coal", 1),
                        smelt("iron_ingot", 3))),
                craft("iron_pickaxe", 1))), plan,
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
                        craft("b", 1))),
                new SequenceNode(List.of(
                        gather("d", 1),
                        craft("c", 1))),
                craft("a", 1))), plan,
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

    @Test
    void resolve_EightIngotsWithCoal_PlansSingleCoal() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 8, KNOWLEDGE);

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 8),
                gather("coal", 1),
                smelt("iron_ingot", 8))), plan,
                "8 слитков углём (8 предметов за уголь) требуют ровно 1 уголь: недобор остановит печь, перебор "
                        + "оставит духа без топлива на следующую плавку");
    }

    @Test
    void resolve_EightIngotsWithoutCoal_PlansSixLogs() {
        ResourceKnowledge logsOnly = new StubKnowledge(
                Set.of("oak_log", "iron_ore"),
                Map.of(),
                Map.of("iron_ingot", "iron_ore"));

        TaskNode plan = ResourceResolver.resolve("iron_ingot", 8, logsOnly);

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 8),
                gather("oak_log", 6),
                smelt("iron_ingot", 8))), plan,
                "Без угля резолвер обязан взять следующее доступное топливо — брёвна (1.5 предмета за бревно, "
                        + "ceil(8 / 1.5) = 6): иначе плавка без угольной жилы не планируется вовсе");
    }

    @Test
    void resolve_SmeltedFuel_PlansInputAndFuelSeparately() {
        ResourceKnowledge charcoalOnly = new StubKnowledge(
                Set.of("oak_log", "iron_ore"),
                Map.of(),
                Map.of("iron_ingot", "iron_ore", "charcoal", "oak_log"));

        TaskNode plan = ResourceResolver.resolve("iron_ingot", 8, charcoalOnly);

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 8),
                new SequenceNode(List.of(
                        gather("oak_log", 1),
                        gather("oak_log", 1),
                        smelt("charcoal", 1))),
                smelt("iron_ingot", 8))), plan,
                "Топливо-древесный уголь планируется своей плавкой: вход (1 бревно) и топливо под него (1 бревно) — "
                        + "два независимых шага, счёт не схлопывается. Иначе дух сожжёт входной запас вместо топлива");
    }

    @Test
    void resolve_SmeltWithoutObtainableFuel_ThrowsNamingSmelted() {
        ResourceKnowledge noFuel = new StubKnowledge(
                Set.of("iron_ore"),
                Map.of(),
                Map.of("iron_ingot", "iron_ore"));

        UnplannableGoalException error = assertThrows(UnplannableGoalException.class,
                () -> ResourceResolver.resolve("iron_ingot", 2, noFuel),
                "Плавка без доступного топлива обязана отвергаться: молчаливый план без топлива выглядит готовым, "
                        + "а печь встанет на первом же предмете");

        assertEquals("iron_ingot", error.item(),
                "Исключение обязано нести плавимый предмет 'iron_ingot': по нему правят знания о топливе");
        assertTrue(error.getMessage().contains("iron_ingot"),
                "Сообщение обязано называть 'iron_ingot'. Фактическое: " + error.getMessage());
    }

    @Test
    void resolve_InventoryCoversFuel_OmitsFuelGather() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 2, KNOWLEDGE, Map.of("coal", 5));

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 2),
                smelt("iron_ingot", 2))), plan,
                "Топливо из инвентаря (5 угля при нужде в 1) обязано гасить шаг добычи: иначе дух потащит лишний "
                        + "уголь, хотя печь уже заправлена");
    }

    @Test
    void resolve_InventoryPartiallyCoversFuel_GathersRemainder() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 9, KNOWLEDGE, Map.of("coal", 1));

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 9),
                gather("coal", 1),
                smelt("iron_ingot", 9))), plan,
                "9 слитков требуют 2 угля, 1 уже в инвентаре — добыть надо ровно 1: полный недобор остановит печь, "
                        + "полный перебор проигнорирует запас");
    }

    @Test
    void resolve_InventoryHoldsLogs_SpendsThemAndGathersRemainder() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 8, KNOWLEDGE, Map.of("oak_log", 2));

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 8),
                gather("oak_log", 4),
                smelt("iron_ingot", 8))), plan,
                "При 2 брёвнах в инвентаре (нужно 6) резолвер обязан потратить имеющиеся и добрать 4 того же топлива, "
                        + "а не переключаться на отсутствующий уголь: иначе дух бросит имеющееся топливо ради нового типа");
    }

    @Test
    void resolve_ThreeSmeltsSharingInventoryFuel_EachConsumesItsShare() {
        ResourceKnowledge forge = new StubKnowledge(
                Set.of("iron_ore", "copper_ore", "gold_ore", "coal"),
                Map.of("gear", List.of(
                        new Ingredient("iron_ingot", 1),
                        new Ingredient("copper_ingot", 9),
                        new Ingredient("gold_ingot", 1))),
                Map.of("iron_ingot", "iron_ore", "copper_ingot", "copper_ore", "gold_ingot", "gold_ore"));

        TaskNode plan = ResourceResolver.resolve("gear", 1, forge, Map.of("coal", 2));

        assertEquals(new SequenceNode(List.of(
                new SequenceNode(List.of(
                        gather("iron_ore", 1),
                        smelt("iron_ingot", 1))),
                new SequenceNode(List.of(
                        gather("copper_ore", 9),
                        gather("coal", 1),
                        smelt("copper_ingot", 9))),
                new SequenceNode(List.of(
                        gather("gold_ore", 1),
                        gather("coal", 1),
                        smelt("gold_ingot", 1))),
                craft("gear", 1))), plan,
                "2 угля в инвентаре обязаны расходиться по плавкам: 1-й слиток тратит 1 (добычи нет), 9 медных тратят "
                        + "последний и добирают 1, золоту добирается 1. Без расхода между плавками вторая и третья "
                        + "посчитают чужой уголь своим и печь встанет");
    }

    @Test
    void resolve_PlanksAvailable_PlansPlanksBeforeCharcoal() {
        ResourceKnowledge sawmill = new StubKnowledge(
                Set.of("iron_ore", "oak_log"),
                Map.of("oak_planks", List.of(new Ingredient("oak_log", 1))),
                Map.of("iron_ingot", "iron_ore", "charcoal", "oak_log"));

        TaskNode plan = ResourceResolver.resolve("iron_ingot", 8, sawmill);

        assertEquals(new SequenceNode(List.of(
                gather("iron_ore", 8),
                new SequenceNode(List.of(
                        gather("oak_log", 6),
                        craft("oak_planks", 6))),
                smelt("iron_ingot", 8))), plan,
                "Без угля резолвер обязан топить досками раньше древесного угля: из бревна выходят 4 доски по 300 "
                        + "тиков — в 4 раза больше горения, чем жечь бревно или плавить его в уголь. 8 слитков требуют "
                        + "6 досок");
    }

    @Test
    void resolve_NullInventory_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> ResourceResolver.resolve("iron_ingot", 2, KNOWLEDGE, null),
                "Null-инвентарь обязан отвергаться: молчаливое чтение null превратит выбор топлива в падение в тике");
    }

    private static ScriptCall craft(String item, int count) {
        return new ScriptCall("craft", Map.of("item", item, "count", count, "radius", STATION_RADIUS));
    }

    private static ScriptCall smelt(String item, int count) {
        return new ScriptCall("smelt", Map.of("item", item, "count", count, "radius", STATION_RADIUS));
    }

    @Test
    void resolve_IntermediateDemandOverLimit_BatchesIntoValidCalls() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of("oak_log"),
                Map.of("stick", List.of(new Ingredient("oak_log", 40))), Map.of());

        TaskNode plan = ResourceResolver.resolve("stick", 8, knowledge);

        assertEquals(new SequenceNode(List.of(
                new SequenceNode(List.of(gather("oak_log", 256), gather("oak_log", 64))),
                craft("stick", 8))), plan,
                "Промежуточное требование 320 (8 крафтов по 40 бревна) обязано разбиваться на вызовы в пределах "
                        + "лимита 256: иначе валидатор ронял бы весь план внутренней ошибкой, а дух оставался бы "
                        + "без плана на достижимую цель. Финальный крафт при этом один, на 8");
    }

    private static ScriptCall gather(String block, int count) {
        return new ScriptCall("gather", Map.of("block", block, "count", count,
                "radius", CatalogLimits.HARVEST_RADIUS_DEFAULT));
    }
}