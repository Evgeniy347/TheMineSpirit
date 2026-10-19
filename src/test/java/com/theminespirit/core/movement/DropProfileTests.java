package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
