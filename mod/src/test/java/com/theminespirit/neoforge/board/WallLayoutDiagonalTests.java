package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.WallLayout;
import com.theminespirit.core.town.WallPiece;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WallLayoutDiagonalTests {
    @Test
    void open_ThickStaircase_LaidWithDiagonalFragments() {
        List<ColumnMask.Cell> line = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            line.add(new ColumnMask.Cell(x, 0));
        }
        for (int step = 0; step < 4; step++) {
            line.add(new ColumnMask.Cell(2 + step, 1 + step));
            line.add(new ColumnMask.Cell(3 + step, 1 + step));
        }
        List<WallPiece> pieces = WallLayout.of(DataTables.INSTANCE.presets().current(), StageSchemes.wall())
                .open(line, List.of());

        assertFalse(
                pieces.isEmpty(),
                "Диагональная лесенка стены раскладывается на фрагменты: пустой список означает, что стену "
                        + "нечем строить"
                        + " Иначе тест open_ThickStaircase_LaidWithDiagonalFragments перестанет ловить эту поломку."
                        + " При смене open_ThickStaircase_LaidWithDiagonalFragments проверь это утверждение.");
        assertTrue(
                pieces.stream().anyMatch(piece -> piece.scheme().contains("diagonal")),
                "Лесенка под 45° кладётся диагональным фрагментом: без него на диагонали остаются прямые и "
                        + "повороты 90°: " + pieces
                        + " Иначе тест open_ThickStaircase_LaidWithDiagonalFragments перестанет ловить эту поломку."
                        + " При смене open_ThickStaircase_LaidWithDiagonalFragments проверь это утверждение.");
    }
}
