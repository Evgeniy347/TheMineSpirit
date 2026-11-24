package com.theminespirit.core.town;

import static com.theminespirit.core.town.WallLayoutTests.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallLayoutOpeningTests {
    private static final WallLayout FENCE =
            WallLayout.of(DataTables.INSTANCE.presets().current(), WallLayoutTests.FENCE_SCHEMES);

    @Test
    void openRuns_StreetOpening_SkipsGateCells() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 24);
        List<Integer> opening = List.of(5, 6, 7, 8, 9);

        List<WallPiece> pieces = FENCE.openRuns(line, opening);

        Set<Integer> covered = new HashSet<>();
        for (WallPiece piece : pieces) {
            for (int index = piece.x(); index < piece.x() + piece.length(); index++) {
                covered.add(index);
            }
        }
        Set<Integer> holes = new HashSet<>(opening);
        assertFalse(
                covered.stream().anyMatch(holes::contains),
                "Проём улицы в стене не застраивается: ворот-объектов нет, проход равен ширине улицы: " + pieces
                        + " Иначе тест openRuns_StreetOpening_SkipsGateCells перестанет ловить эту поломку. При смене openRuns_StreetOpening_SkipsGateCells проверь это утверждение.");
        assertEquals(
                19,
                covered.size(),
                "Стена закрывает все клетки линии, кроме проёма: " + pieces
                        + " Иначе тест openRuns_StreetOpening_SkipsGateCells перестанет ловить эту поломку. При смене openRuns_StreetOpening_SkipsGateCells проверь это утверждение.");
    }

    @Test
    void openParts_StreetOpening_SamePiecesAsWhole() {
        List<ColumnMask.Cell> line = run(new ArrayList<>(), 0, 0, 1, 0, 24);
        List<Integer> opening = List.of(5, 6, 7, 8, 9);

        List<WallPiece> whole = FENCE.openRuns(line, opening);
        List<List<WallPiece>> parts = FENCE.openParts(line, opening, 3);
        List<WallPiece> flat = new ArrayList<>();
        parts.forEach(flat::addAll);

        assertEquals(
                3,
                parts.size(),
                "Стена делится ровно на запрошенное число частей: " + parts
                        + " Иначе тест openParts_StreetOpening_SamePiecesAsWhole перестанет ловить эту поломку. При смене openParts_StreetOpening_SamePiecesAsWhole проверь это утверждение.");
        assertEquals(
                whole,
                flat,
                "Части стены вместе дают ту же раскладку, что и целая линия: " + parts
                        + " Иначе тест openParts_StreetOpening_SamePiecesAsWhole перестанет ловить эту поломку. При смене openParts_StreetOpening_SamePiecesAsWhole проверь это утверждение.");
    }
}
