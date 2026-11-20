package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.DropProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DropProfileTests {
    record ProfileRow(List<Integer> floors, boolean blocked) {
        @Override
        public String toString() {
            return "пол " + floors;
        }
    }

    static Stream<Arguments> profileTable() {
        return Stream.of(
                Arguments.of(new ProfileRow(List.of(), false)),
                Arguments.of(new ProfileRow(List.of(5), false)),
                Arguments.of(new ProfileRow(List.of(3, 3, 3), false)),
                Arguments.of(new ProfileRow(List.of(3, 3, 2), false)),
                Arguments.of(new ProfileRow(List.of(3, 3, 1), false)),
                Arguments.of(new ProfileRow(List.of(3, 3, 0), true)),
                Arguments.of(new ProfileRow(List.of(5, 5, 5, 2, 2), true)),
                Arguments.of(new ProfileRow(List.of(1, 2, 3, 4), false)),
                Arguments.of(new ProfileRow(List.of(1, 4, 1), true)),
                Arguments.of(new ProfileRow(List.of(7, 5), false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileTable")
    void blocksCarefulStep_HeightProfile_BlockDecisionMatches(ProfileRow row) {
        assertEquals(
                row.blocked(),
                DropProfile.blocksCarefulStep(row.floors()),
                "Профиль " + row.floors() + (row.blocked() ? " обязан запрещать" : " обязан разрешать")
                        + " осторожный шаг: пропуск обрыва роняет духа, а ложный запрет останавливает его на ровном месте"
                        + " Иначе тест blocksCarefulStep_HeightProfile_BlockDecisionMatches перестанет ловить эту поломку. При смене blocksCarefulStep_HeightProfile_BlockDecisionMatches проверь это утверждение.");
    }

    @Test
    void maxSafeDrop_ValueCheck_EqualsTwo() {
        assertEquals(
                2,
                DropProfile.MAX_SAFE_DROP,
                "Безопасный спуск — 2 блока: большее падение ранит духа, осторожный шаг через такой край "
                        + "не строится; при осознанной смене сверьте политики безопасности"
                        + " Иначе тест maxSafeDrop_ValueCheck_EqualsTwo перестанет ловить эту поломку. При смене maxSafeDrop_ValueCheck_EqualsTwo проверь это утверждение.");
    }

    @Test
    void lookahead_ValueCheck_EqualsEight() {
        assertEquals(
                8,
                DropProfile.LOOKAHEAD_CELLS,
                "Заглядывание осторожного шага — 8 клеток пути: короче — поздняя остановка перед обрывом, "
                        + "длиннее — лишние чтения столбцов при каждом перестроении пути"
                        + " Иначе тест lookahead_ValueCheck_EqualsEight перестанет ловить эту поломку. При смене lookahead_ValueCheck_EqualsEight проверь это утверждение.");
    }

    @Test
    void carefulRefusal_FlatOrTwoBlockDrop_Passes() {
        assertNull(
                DropProfile.carefulRefusal(true, true, List.of(5, 5, 5, 5)),
                "Ровный путь осторожный шаг обязан пропускать, иначе дух стоит на ровном месте"
                        + " При смене carefulRefusal_FlatOrTwoBlockDrop_Passes проверь это утверждение.");
        assertNull(
                DropProfile.carefulRefusal(true, true, List.of(5, 5, 3, 3)),
                "Спуск на 2 блока (MAX_SAFE_DROP) осторожный шаг обязан пропускать"
                        + " Иначе тест carefulRefusal_FlatOrTwoBlockDrop_Passes перестанет ловить эту поломку. При смене carefulRefusal_FlatOrTwoBlockDrop_Passes проверь это утверждение.");
    }

    @Test
    void carefulRefusal_ThreeBlockDrop_RefusesWithDropReason() {
        String reason = DropProfile.carefulRefusal(true, true, List.of(5, 5, 2, 2));
        assertTrue(
                reason != null && reason.contains("обрыв"),
                "Спуск на 3 блока обязан отказать с причиной про обрыв, иначе дух срывается вниз: " + reason
                        + " При смене carefulRefusal_ThreeBlockDrop_RefusesWithDropReason проверь это утверждение.");
    }

    @Test
    void carefulRefusal_PathMissingOrNotReaching_Refuses() {
        assertTrue(
                String.valueOf(DropProfile.carefulRefusal(true, false, List.of(5, 5, 5, 5)))
                        .contains("не доходит"),
                "Путь, не доходящий до цели, осторожный шаг обязан отвергать: цель за преградой или обрывом"
                        + " Иначе тест carefulRefusal_PathMissingOrNotReaching_Refuses перестанет ловить эту поломку. При смене carefulRefusal_PathMissingOrNotReaching_Refuses проверь это утверждение.");
        assertTrue(
                String.valueOf(DropProfile.carefulRefusal(false, false, List.of()))
                        .contains("пути к цели нет"),
                "Отсутствие пути осторожный шаг обязан отвергать"
                        + " Иначе тест carefulRefusal_PathMissingOrNotReaching_Refuses перестанет ловить эту поломку. При смене carefulRefusal_PathMissingOrNotReaching_Refuses проверь это утверждение.");
    }

    @Test
    void carefulRefusal_DropBeyondLookahead_PassesUntilRepath() {
        List<Integer> heights = new ArrayList<>(Collections.nCopies(DropProfile.LOOKAHEAD_CELLS + 3, 9));
        heights.set(heights.size() - 1, 4);
        assertNull(
                DropProfile.carefulRefusal(true, true, heights),
                "Обрыв дальше " + DropProfile.LOOKAHEAD_CELLS + " клеток осторожный шаг пока не проверяет: "
                        + "он увидит его при следующем перестроении пути"
                        + " Иначе тест carefulRefusal_DropBeyondLookahead_PassesUntilRepath перестанет ловить эту поломку. При смене carefulRefusal_DropBeyondLookahead_PassesUntilRepath проверь это утверждение.");
    }

    @Test
    void routeStepDrop_Limits_MatchPlayerSafeFall() {
        assertEquals(
                3,
                DropProfile.MAX_STEP_DROP,
                "Предел безопасного спуска — 3 блока: с четвёртого начинается урон от падения"
                        + " Иначе тест routeStepDrop_Limits_MatchPlayerSafeFall перестанет ловить эту поломку. При смене routeStepDrop_Limits_MatchPlayerSafeFall проверь это утверждение.");
        assertEquals(
                0,
                DropProfile.fallDamage(3),
                "Спуск на 3 блока урона не наносит"
                        + " Иначе тест routeStepDrop_Limits_MatchPlayerSafeFall перестанет ловить эту поломку. При смене routeStepDrop_Limits_MatchPlayerSafeFall проверь это утверждение.");
        assertEquals(
                8,
                DropProfile.fallDamage(11),
                "Падение на 11 блоков — 8 урона: дух почти разбивается"
                        + " Иначе тест routeStepDrop_Limits_MatchPlayerSafeFall перестанет ловить эту поломку. При смене routeStepDrop_Limits_MatchPlayerSafeFall проверь это утверждение.");
        assertFalse(
                DropProfile.lethalDrop(11, 20.0F),
                "Падение на 11 блоков не смертельно при полном здоровье 20: запрещать такой спуск — "
                        + "запереть духа на опоре навсегда"
                        + " Иначе тест routeStepDrop_Limits_MatchPlayerSafeFall перестанет ловить эту поломку. При смене routeStepDrop_Limits_MatchPlayerSafeFall проверь это утверждение.");
        assertTrue(
                DropProfile.lethalDrop(11, 8.0F),
                "Падение на 11 блоков при здоровье 8 обязано считаться смертельным: дух разобьётся"
                        + " Иначе тест routeStepDrop_Limits_MatchPlayerSafeFall перестанет ловить эту поломку. При смене routeStepDrop_Limits_MatchPlayerSafeFall проверь это утверждение.");
        assertFalse(
                DropProfile.lethalDrop(3, 1.0F),
                "Спуск на 3 блока не смертелен даже с одним здоровьем: урона нет"
                        + " Иначе тест routeStepDrop_Limits_MatchPlayerSafeFall перестанет ловить эту поломку. При смене routeStepDrop_Limits_MatchPlayerSafeFall проверь это утверждение.");
    }

    @Test
    void carefulRefusal_DropAtLookaheadBoundary_Refuses() {
        List<Integer> heights = new ArrayList<>(Collections.nCopies(DropProfile.LOOKAHEAD_CELLS + 1, 9));
        heights.set(heights.size() - 1, 4);
        assertTrue(
                String.valueOf(DropProfile.carefulRefusal(true, true, heights)).contains("обрыв"),
                "Обрыв ровно на границе заглядывания (клетка " + DropProfile.LOOKAHEAD_CELLS + ") обязан отказать"
                        + " Иначе тест carefulRefusal_DropAtLookaheadBoundary_Refuses перестанет ловить эту поломку. При смене carefulRefusal_DropAtLookaheadBoundary_Refuses проверь это утверждение.");
    }
}
