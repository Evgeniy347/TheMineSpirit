package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                        + " осторожный шаг: пропуск обрыва роняет духа, а ложный запрет останавливает его на ровном месте");
    }

    @Test
    void maxSafeDrop_ValueCheck_EqualsTwo() {
        assertEquals(
                2,
                DropProfile.MAX_SAFE_DROP,
                "Безопасный спуск — 2 блока: большее падение ранит духа, осторожный шаг через такой край "
                        + "не строится; при осознанной смене сверьте политики безопасности");
    }

    @Test
    void lookahead_ValueCheck_EqualsEight() {
        assertEquals(
                8,
                DropProfile.LOOKAHEAD_CELLS,
                "Заглядывание осторожного шага — 8 клеток пути: короче — поздняя остановка перед обрывом, "
                        + "длиннее — лишние чтения столбцов при каждом перестроении пути");
    }

    @Test
    void carefulRefusal_FlatOrTwoBlockDrop_Passes() {
        assertNull(
                DropProfile.carefulRefusal(true, true, List.of(5, 5, 5, 5)),
                "Ровный путь осторожный шаг обязан пропускать, иначе дух стоит на ровном месте");
        assertNull(
                DropProfile.carefulRefusal(true, true, List.of(5, 5, 3, 3)),
                "Спуск на 2 блока (MAX_SAFE_DROP) осторожный шаг обязан пропускать");
    }

    @Test
    void carefulRefusal_ThreeBlockDrop_RefusesWithDropReason() {
        String reason = DropProfile.carefulRefusal(true, true, List.of(5, 5, 2, 2));
        assertTrue(
                reason != null && reason.contains("обрыв"),
                "Спуск на 3 блока обязан отказать с причиной про обрыв, иначе дух срывается вниз: " + reason);
    }

    @Test
    void carefulRefusal_PathMissingOrNotReaching_Refuses() {
        assertTrue(
                String.valueOf(DropProfile.carefulRefusal(true, false, List.of(5, 5, 5, 5)))
                        .contains("не доходит"),
                "Путь, не доходящий до цели, осторожный шаг обязан отвергать: цель за преградой или обрывом");
        assertTrue(
                String.valueOf(DropProfile.carefulRefusal(false, false, List.of()))
                        .contains("пути к цели нет"),
                "Отсутствие пути осторожный шаг обязан отвергать");
    }

    @Test
    void carefulRefusal_DropBeyondLookahead_PassesUntilRepath() {
        List<Integer> heights = new ArrayList<>(Collections.nCopies(DropProfile.LOOKAHEAD_CELLS + 3, 9));
        heights.set(heights.size() - 1, 4);
        assertNull(
                DropProfile.carefulRefusal(true, true, heights),
                "Обрыв дальше " + DropProfile.LOOKAHEAD_CELLS + " клеток осторожный шаг пока не проверяет: "
                        + "он увидит его при следующем перестроении пути");
    }

    @Test
    void carefulRefusal_DropAtLookaheadBoundary_Refuses() {
        List<Integer> heights = new ArrayList<>(Collections.nCopies(DropProfile.LOOKAHEAD_CELLS + 1, 9));
        heights.set(heights.size() - 1, 4);
        assertTrue(
                String.valueOf(DropProfile.carefulRefusal(true, true, heights)).contains("обрыв"),
                "Обрыв ровно на границе заглядывания (клетка " + DropProfile.LOOKAHEAD_CELLS + ") обязан отказать");
    }
}
