package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkSquareTests {
    @Test
    void chunks_RadiusTwo_ContainsTwentyFiveCells() {
        Set<ChunkCoord> chunks = ChunkSquare.chunks(new ChunkCoord(0, 0), 2);

        assertEquals(
                25,
                chunks.size(),
                "Квадрат радиуса R обязан содержать (2R+1)^2 чанков: при R=2 это 25. Если размер другой, "
                        + "дух удержит не ту зону, что задумано"
                        + " Иначе тест chunks_RadiusTwo_ContainsTwentyFiveCells перестанет ловить эту поломку. При смене chunks_RadiusTwo_ContainsTwentyFiveCells проверь это утверждение.");
    }

    @Test
    void chunks_RadiusZero_ContainsOnlyCenter() {
        Set<ChunkCoord> chunks = ChunkSquare.chunks(new ChunkCoord(5, 7), 0);

        assertEquals(
                Set.of(new ChunkCoord(5, 7)),
                chunks,
                "При R=0 дух обязан держать ровно один чанк — свой. Иначе граница 'не загружать лишнего' "
                        + "поедет в обе стороны"
                        + " При смене chunks_RadiusZero_ContainsOnlyCenter проверь это утверждение.");
    }

    @Test
    void chunks_NegativeRadius_ThrowsIllegalArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkSquare.chunks(new ChunkCoord(0, 0), -1),
                "Отрицательный радиус — ошибка конфигурации: молча принятый, он даёт пустой набор и дух "
                        + "замирает без чанков. Ожидается IllegalArgumentException"
                        + " Иначе тест chunks_NegativeRadius_ThrowsIllegalArgument перестанет ловить эту поломку. При смене chunks_NegativeRadius_ThrowsIllegalArgument проверь это утверждение.");
    }

    @Test
    void diff_SameCenter_IsEmpty() {
        ChunkDiff diff = ChunkSquare.diff(new ChunkCoord(3, -2), new ChunkCoord(3, -2), 4);

        assertEquals(
                Set.of(),
                diff.toAdd(),
                "Без смены чанка добавлять нечего: иначе каждый тик дух будет дёргать одни и те же билеты"
                        + " При смене diff_SameCenter_IsEmpty проверь это утверждение.");
        assertEquals(
                Set.of(),
                diff.toRemove(),
                "Без смены чанка снимать нечего: обратный вызов билетов на месте обнулит загрузку"
                        + " Иначе тест diff_SameCenter_IsEmpty перестанет ловить эту поломку. При смене diff_SameCenter_IsEmpty проверь это утверждение.");
    }

    @Test
    void diff_ShiftOneChunk_AddsAndRemovesWholeRow() {
        int radius = 3;
        ChunkDiff diff = ChunkSquare.diff(new ChunkCoord(0, 0), new ChunkCoord(1, 0), radius);

        assertEquals(
                2 * radius + 1,
                diff.toAdd().size(),
                "Сдвиг на один чанк обязан добавить ровно один ряд из 2R+1 чанков: дух не должен "
                        + "перезагружать всю зону заново"
                        + " Иначе тест diff_ShiftOneChunk_AddsAndRemovesWholeRow перестанет ловить эту поломку. При смене diff_ShiftOneChunk_AddsAndRemovesWholeRow проверь это утверждение.");
        assertEquals(
                2 * radius + 1,
                diff.toRemove().size(),
                "Сдвиг на один чанк обязан снять ровно один ряд из 2R+1 чанков: иначе ушедшие чанки "
                        + "останутся висеть нагруженными"
                        + " При смене diff_ShiftOneChunk_AddsAndRemovesWholeRow проверь это утверждение.");
    }

    @Test
    void diff_OldOverDisjointArea_ReplacesEveryCoordinate() {
        int radius = 2;
        ChunkDiff diff = ChunkSquare.diff(new ChunkCoord(0, 0), new ChunkCoord(100, 100), radius);

        assertEquals(
                25,
                diff.toAdd().size(),
                "При уходе вдаль весь новый квадрат состоит из новых чанков: 25 добавлений при R=2"
                        + " Иначе тест diff_OldOverDisjointArea_ReplacesEveryCoordinate перестанет ловить эту поломку. При смене diff_OldOverDisjointArea_ReplacesEveryCoordinate проверь это утверждение.");
        assertEquals(
                25,
                diff.toRemove().size(),
                "При уходе вдаль прежний квадрат целиком снимается: иначе старые чанки останутся нагруженными"
                        + " При смене diff_OldOverDisjointArea_ReplacesEveryCoordinate проверь это утверждение.");
    }

    @Test
    void apply_OldSquareWithDiff_EqualsDesiredSquare() {
        int radius = 2;
        ChunkCoord oldCenter = new ChunkCoord(0, 0);
        ChunkCoord newCenter = new ChunkCoord(2, -1);
        Set<ChunkCoord> current = ChunkSquare.chunks(oldCenter, radius);
        ChunkDiff diff = ChunkSquare.diff(oldCenter, newCenter, radius);

        Set<ChunkCoord> applied = ChunkSquare.apply(current, diff);

        assertEquals(
                ChunkSquare.chunks(newCenter, radius),
                applied,
                "Текущий набор минус снятые плюс добавленные обязан равняться новому квадрату: иначе "
                        + "применение билетов оставит набор несогласованным с положением духа"
                        + " При смене apply_OldSquareWithDiff_EqualsDesiredSquare проверь это утверждение.");
    }
}
