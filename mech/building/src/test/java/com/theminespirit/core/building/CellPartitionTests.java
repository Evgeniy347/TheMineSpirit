package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class CellPartitionTests {
    private static final BlockCoord A = new BlockCoord(0, 0, 0);
    private static final BlockCoord B = new BlockCoord(1, 0, 0);
    private static final BlockCoord C = new BlockCoord(2, 0, 0);
    private static final BlockCoord D = new BlockCoord(3, 0, 0);
    private static final BlockCoord E = new BlockCoord(4, 0, 0);
    private static final BlockCoord F = new BlockCoord(5, 0, 0);

    @Test
    void split_OnePart_KeepsWholeOrder() {
        List<List<BlockCoord>> shares = CellPartition.split(List.of(A, B, C), 1);

        assertEquals(
                List.of(List.of(A, B, C)),
                shares,
                "Одна доля — весь список без изменения порядка: иначе одиночный строитель терял бы клетки"
                        + " При смене split_OnePart_KeepsWholeOrder проверь это утверждение.");
    }

    @Test
    void split_TwoParts_AlternateCellsWithoutGaps() {
        List<List<BlockCoord>> shares = CellPartition.split(List.of(A, B, C, D, E, F), 2);

        assertEquals(
                List.of(List.of(A, C, E), List.of(B, D, F)),
                shares,
                "Чередование index % parts даёт каждой доле клетки через одну: иначе доля одного слоя ложится "
                        + "целиком на одного и верхние блоки строятся без опоры"
                        + " При смене split_TwoParts_AlternateCellsWithoutGaps проверь это утверждение.");
    }

    @Test
    void split_EvenCount_AllPartsEqualSize() {
        List<List<BlockCoord>> shares = CellPartition.split(List.of(A, B, C, D), 2);

        assertEquals(
                2,
                shares.get(0).size(),
                "Ровное число клеток делится поровну"
                        + " Иначе тест split_EvenCount_AllPartsEqualSize перестанет ловить эту поломку. При смене split_EvenCount_AllPartsEqualSize проверь это утверждение.");
        assertEquals(
                2,
                shares.get(1).size(),
                "Ровное число клеток делится поровну"
                        + " Иначе тест split_EvenCount_AllPartsEqualSize перестанет ловить эту поломку. При смене split_EvenCount_AllPartsEqualSize проверь это утверждение.");
    }

    @Test
    void split_UnevenCount_FirstPartsGetExtra() {
        List<List<BlockCoord>> shares = CellPartition.split(List.of(A, B, C, D, E), 2);

        assertEquals(
                List.of(A, C, E),
                shares.get(0),
                "Остаток 1 достаётся первой доле"
                        + " Иначе тест split_UnevenCount_FirstPartsGetExtra перестанет ловить эту поломку. При смене split_UnevenCount_FirstPartsGetExtra проверь это утверждение.");
        assertEquals(
                List.of(B, D),
                shares.get(1),
                "Вторая доля короче на остаток"
                        + " Иначе тест split_UnevenCount_FirstPartsGetExtra перестанет ловить эту поломку. При смене split_UnevenCount_FirstPartsGetExtra проверь это утверждение.");
    }

    @Test
    void split_MorePartsThanCells_TailSharesEmpty() {
        List<List<BlockCoord>> shares = CellPartition.split(List.of(A, B), 4);

        assertEquals(
                4,
                shares.size(),
                "Долей ровно столько, сколько духов в отряде"
                        + " Иначе тест split_MorePartsThanCells_TailSharesEmpty перестанет ловить эту поломку. При смене split_MorePartsThanCells_TailSharesEmpty проверь это утверждение.");
        assertEquals(
                List.of(A),
                shares.get(0),
                "Первая клетка — первой доле"
                        + " Иначе тест split_MorePartsThanCells_TailSharesEmpty перестанет ловить эту поломку. При смене split_MorePartsThanCells_TailSharesEmpty проверь это утверждение.");
        assertEquals(
                List.of(B),
                shares.get(1),
                "Вторая клетка — второй доле"
                        + " Иначе тест split_MorePartsThanCells_TailSharesEmpty перестанет ловить эту поломку. При смене split_MorePartsThanCells_TailSharesEmpty проверь это утверждение.");
        assertTrue(
                shares.get(2).isEmpty() && shares.get(3).isEmpty(),
                "Лишним духам достаются пустые доли: они потом помогают по общему списку"
                        + " Иначе тест split_MorePartsThanCells_TailSharesEmpty перестанет ловить эту поломку. При смене split_MorePartsThanCells_TailSharesEmpty проверь это утверждение.");
    }

    @Test
    void split_EmptyList_AllPartsEmpty() {
        List<List<BlockCoord>> shares = CellPartition.split(List.of(), 3);

        assertEquals(
                3,
                shares.size(),
                "Пустой список всё равно делится на число долей отряда"
                        + " Иначе тест split_EmptyList_AllPartsEmpty перестанет ловить эту поломку. При смене split_EmptyList_AllPartsEmpty проверь это утверждение.");
        assertTrue(
                shares.stream().allMatch(List::isEmpty),
                "Пустому списку нечего делить"
                        + " Иначе тест split_EmptyList_AllPartsEmpty перестанет ловить эту поломку. При смене split_EmptyList_AllPartsEmpty проверь это утверждение.");
    }

    @Test
    void split_NullList_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> CellPartition.split(null, 2),
                "null вместо списка не говорит, есть ли клетки: передавайте пустой список"
                        + " Иначе тест split_NullList_Throws перестанет ловить эту поломку. При смене split_NullList_Throws проверь это утверждение.");
    }

    @Test
    void split_ZeroParts_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CellPartition.split(List.of(A), 0),
                "Ноль долей некуда раздавать клетки: разбиение вырождено"
                        + " Иначе тест split_ZeroParts_Throws перестанет ловить эту поломку. При смене split_ZeroParts_Throws проверь это утверждение.");
    }

    @Test
    void split_NegativeParts_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CellPartition.split(List.of(A), -1),
                "Отрицательное число долей противоречит разбиению: клетки некому отдать"
                        + " Иначе тест split_NegativeParts_Throws перестанет ловить эту поломку. При смене split_NegativeParts_Throws проверь это утверждение.");
    }
}
