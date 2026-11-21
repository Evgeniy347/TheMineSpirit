package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class GatherBatchTests {

    @Test
    void round_Shortage_RoundsUpToWholeCrafts() {
        assertEquals(
                8,
                GatherBatch.round(5, 4),
                "Недостача 5 досок при выходе 4 за крафт — партия 8: acquire не делит рецепт, "
                        + "иначе дух не добудет последнюю доску"
                        + " При смене round_Shortage_RoundsUpToWholeCrafts проверь это утверждение.");
    }

    @Test
    void round_ExactMultiple_Stays() {
        assertEquals(
                16,
                GatherBatch.round(16, 4),
                "Недостача, кратная выходу, не округляется вверх: лишняя добыча — лишняя работа"
                        + " Иначе тест round_ExactMultiple_Stays перестанет ловить эту поломку. При смене round_ExactMultiple_Stays проверь это утверждение.");
    }

    @Test
    void round_NoShortage_Zero() {
        assertEquals(
                0,
                GatherBatch.round(0, 4),
                "Нет недостачи — нет партии: нулевая добыча не ставится"
                        + " Иначе тест round_NoShortage_Zero перестанет ловить эту поломку. При смене round_NoShortage_Zero проверь это утверждение.");
    }

    @Test
    void round_OutputOne_KeepsShortage() {
        assertEquals(
                7,
                GatherBatch.round(7, 1),
                "Материал без рецепта (выход 1) добывается ровно по недостаче"
                        + " Иначе тест round_OutputOne_KeepsShortage перестанет ловить эту поломку. При смене round_OutputOne_KeepsShortage проверь это утверждение.");
    }

    @Property
    void round_AnyShortage_NotBelowShortage(
            @ForAll @IntRange(min = 1, max = 512) int shortage, @ForAll @IntRange(min = 1, max = 64) int output) {
        int batch = GatherBatch.round(shortage, output);

        assertEquals(
                0,
                batch % output,
                "Партия кратна выходу рецепта: " + batch + " не делится на " + output
                        + " Иначе тест round_AnyShortage_NotBelowShortage перестанет ловить эту поломку. При смене round_AnyShortage_NotBelowShortage проверь это утверждение.");
        assertTrue(
                batch >= shortage,
                "Партия не меньше недостачи: " + batch + " < " + shortage
                        + " Иначе тест round_AnyShortage_NotBelowShortage перестанет ловить эту поломку. При смене round_AnyShortage_NotBelowShortage проверь это утверждение.");
    }

    @Test
    void split_LargeBatchTwoHelpers_TwoWholeCraftPortions() {
        assertEquals(
                List.of(36, 36),
                GatherBatch.split(72, 3, 3),
                "забор ×72 — на двух духов целыми крафтами по 3: один дух добывал всё (24.10)"
                        + " Иначе тест split_LargeBatchTwoHelpers_TwoWholeCraftPortions перестанет ловить эту поломку. При смене split_LargeBatchTwoHelpers_TwoWholeCraftPortions проверь это утверждение.");
        assertEquals(
                List.of(9, 8),
                GatherBatch.split(17, 1, 2),
                "булыжник 17 — две доли"
                        + " Иначе тест split_LargeBatchTwoHelpers_TwoWholeCraftPortions перестанет ловить эту поломку. При смене split_LargeBatchTwoHelpers_TwoWholeCraftPortions проверь это утверждение.");
    }

    @Test
    void split_SmallOrOneHelper_OnePortion() {
        assertEquals(
                List.of(17),
                GatherBatch.split(17, 1, 1),
                "кирка у одного духа — одна доля: вторая падала «нужен инструмент (кирка)» (24.10)"
                        + " Иначе тест split_SmallOrOneHelper_OnePortion перестанет ловить эту поломку. При смене split_SmallOrOneHelper_OnePortion проверь это утверждение.");
        assertEquals(
                List.of(3),
                GatherBatch.split(3, 1, 3),
                "мелкая добыча не делится"
                        + " Иначе тест split_SmallOrOneHelper_OnePortion перестанет ловить эту поломку. При смене split_SmallOrOneHelper_OnePortion проверь это утверждение.");
    }
}
