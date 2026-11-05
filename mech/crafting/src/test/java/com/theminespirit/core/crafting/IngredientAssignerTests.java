package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IngredientAssignerTests {
    private static final IngredientSpec ANY_PLANKS = IngredientSpec.of("oak_planks", "birch_planks", "spruce_planks");
    private static final IngredientSpec STICK = IngredientSpec.of("stick");

    @Test
    void assign_MixedPlanksForPickaxe_UsesWhatSpiritHas() {
        List<IngredientSpec> pickaxe = List.of(ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, STICK, STICK);
        Map<String, Integer> available = Map.of("birch_planks", 1, "oak_planks", 2, "stick", 2);

        Optional<List<String>> assignment = IngredientAssigner.assign(pickaxe, available);

        assertTrue(
                assignment.isPresent(),
                "Кирка из смеси досок (1 берёза + 2 дуба) обязана собираться: ингредиент 'любые доски' берёт "
                        + "любую породу, иначе дух с разными досками не скрафтит ничего");
        Map<String, Integer> used = new HashMap<>();
        assignment.get().forEach(item -> used.merge(item, 1, Integer::sum));
        assertEquals(
                Map.of("birch_planks", 1, "oak_planks", 2, "stick", 2),
                used,
                "Назначение обязано расходовать ровно столько, сколько мест в рецепте: 3 доски любых пород и 2 палки");
    }

    @Test
    void assign_ScarceItemNeededByOneSlot_OtherSlotTakesAnythingElse() {
        List<IngredientSpec> slots =
                List.of(IngredientSpec.of("birch_planks", "oak_planks"), IngredientSpec.of("birch_planks"));
        Map<String, Integer> available = Map.of("birch_planks", 1, "oak_planks", 1);

        Optional<List<String>> assignment = IngredientAssigner.assign(slots, available);

        assertEquals(
                Optional.of(List.of("oak_planks", "birch_planks")),
                assignment,
                "Берёзовые доски одни, и нужны второму месту: первое место обязано отдать их и взять дубовые. "
                        + "Жадный выбор 'первый подходящий' оставил бы второе место без доски и объявил рецепт "
                        + "невыполнимым при наличии материалов");
    }

    @Test
    void assign_ChainOfDisplacements_FindsSolution() {
        List<IngredientSpec> slots =
                List.of(IngredientSpec.of("b", "a"), IngredientSpec.of("b", "c"), IngredientSpec.of("c"));
        Map<String, Integer> available = Map.of("a", 1, "b", 1, "c", 1);

        Optional<List<String>> assignment = IngredientAssigner.assign(slots, available);

        assertEquals(
                Optional.of(List.of("a", "b", "c")),
                assignment,
                "Цепочка вытеснений обязана разрешаться: простой перебор слева направо тут ошибается");
    }

    @Test
    void assign_NotEnoughItems_ReturnsEmpty() {
        List<IngredientSpec> slots = List.of(ANY_PLANKS, ANY_PLANKS, ANY_PLANKS);
        Map<String, Integer> available = Map.of("oak_planks", 2);

        assertEquals(
                Optional.empty(),
                IngredientAssigner.assign(slots, available),
                "Трёх мест на двух досках не заполнить: ассигнатор обязан вернуть пусто, а не частичный ответ");
    }

    @Test
    void assign_ZeroCountInAvailable_IsTreatedAsAbsent() {
        assertEquals(
                Optional.empty(),
                IngredientAssigner.assign(List.of(STICK), Map.of("stick", 0)),
                "Предмет с нулевым наличием не может занять место рецепта");
    }

    @Test
    void assign_AnyCall_DoesNotChangeAvailable() {
        Map<String, Integer> available = new HashMap<>(Map.of("stick", 2));

        IngredientAssigner.assign(List.of(STICK, STICK), available);

        assertEquals(
                Map.of("stick", 2),
                available,
                "Ассигнатор не вправе менять переданное наличие: расход ведёт вызывающий, иначе расчёт нескольких "
                        + "крафтов подряд считал бы одни и те же предметы дважды");
    }

    @Test
    void assign_NoIngredients_ReturnsEmptyAssignment() {
        assertEquals(
                Optional.of(List.of()),
                IngredientAssigner.assign(List.of(), Map.of()),
                "Рецепт без мест выполним пустым назначением: вырожденный случай не должен падать");
    }
}
