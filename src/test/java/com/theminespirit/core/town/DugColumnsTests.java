package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class DugColumnsTests {
    @Test
    void top_HighestDigPerColumn_Wins() {
        DugColumns columns = new DugColumns();

        columns.remember(1, 64, 2);
        columns.remember(1, 61, 2);
        columns.remember(1, 63, 2);

        assertEquals(OptionalInt.of(64), columns.top(1, 2), "яма считается от самой высокой снятой клетки");
    }

    @Test
    void top_OtherColumns_DoNotMix() {
        DugColumns columns = new DugColumns();
        columns.remember(1, 64, 2);

        assertEquals(OptionalInt.empty(), columns.top(1, 3), "учёт ям — по колонке, а не по всему участку");
        assertEquals(OptionalInt.empty(), columns.top(2, 2), "соседняя колонка не наследует чужую яму");
    }

    @Test
    void columns_NegativeCoordinates_RoundTrip() {
        DugColumns columns = new DugColumns();
        columns.remember(-17, 70, -300);

        assertEquals(
                List.of(new DugColumns.Column(-17, -300, 70)), columns.columns(), "координаты колонки не теряют знак");
    }

    @Test
    void columns_OverLimit_DropsOldest() {
        DugColumns columns = new DugColumns();
        for (int index = 0; index <= DugColumns.MAX_COLUMNS; index++) {
            columns.remember(index, 64, 0);
        }

        assertEquals(DugColumns.MAX_COLUMNS, columns.size(), "учёт ям ограничен по числу колонок");
        assertTrue(columns.top(0, 0).isEmpty(), "самая старая колонка забывается первой");
        assertEquals(OptionalInt.of(64), columns.top(DugColumns.MAX_COLUMNS, 0), "новая колонка сохраняется");
    }
}
