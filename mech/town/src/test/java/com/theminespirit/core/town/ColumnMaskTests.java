package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

class ColumnMaskTests {
    @Test
    void equals_SameCells_True() {
        assertTrue(
                ColumnMask.rect(0, 0, 9, 9).equals(ColumnMask.rect(0, 0, 9, 9)),
                "Маски одной формы равны: сравнение идёт по битам и границам, иначе кэш плана не попадает"
                        + " Иначе тест equals_SameCells_True перестанет ловить эту поломку. При смене equals_SameCells_True проверь это утверждение.");
    }

    @Test
    void equals_DifferentCells_False() {
        assertFalse(
                ColumnMask.rect(0, 0, 9, 9).equals(ColumnMask.rect(0, 0, 9, 8)),
                "Маски разной формы не равны: иначе защита перепутает участки"
                        + " Иначе тест equals_DifferentCells_False перестанет ловить эту поломку. При смене equals_DifferentCells_False проверь это утверждение.");
    }

    @Test
    void equals_TrailingZeroWords_True() {
        BitSet sparse = new BitSet(1024);
        sparse.set(0, 100);

        assertTrue(
                new ColumnMask(0, 0, 10, 10, sparse).equals(ColumnMask.rect(0, 0, 9, 9)),
                "Лишние нулевые слова хранения не мешают равенству: сравнение по битам дешевле перечисления всех клеток"
                        + " Иначе тест equals_TrailingZeroWords_True перестанет ловить эту поломку. При смене equals_TrailingZeroWords_True проверь это утверждение.");
    }

    @Test
    void hashCode_SameCells_Same() {
        assertEquals(
                ColumnMask.rect(-5, -7, 4, 2).hashCode(),
                ColumnMask.rect(-5, -7, 4, 2).hashCode(),
                "Равные маски дают один хеш: иначе маска теряется в HashMap и план пересобирается на каждый блок"
                        + " Иначе тест hashCode_SameCells_Same перестанет ловить эту поломку. При смене hashCode_SameCells_Same проверь это утверждение.");
    }
}
