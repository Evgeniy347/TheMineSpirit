package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockedCellsTests {
    private static final BlockCoord CELL = new BlockCoord(10, 64, 5);

    @Test
    void note_SameCellTwice_KeepsOneMarkAndFirstSince() {
        BlockedCells cells = new BlockedCells(100);
        cells.note(CELL, 50);
        cells.note(CELL, 60);
        List<BlockedCells.Mark> live = cells.live(60);
        assertEquals(
                1,
                live.size(),
                "повторная метка той же клетки не плодит задания: " + live
                        + " Иначе тест note_SameCellTwice_KeepsOneMarkAndFirstSince перестанет ловить эту поломку. При смене note_SameCellTwice_KeepsOneMarkAndFirstSince проверь это утверждение.");
        assertEquals(
                50,
                live.get(0).since(),
                "срок первой метки не сдвигается повтором: иначе дедуп по id ломается"
                        + " При смене note_SameCellTwice_KeepsOneMarkAndFirstSince проверь это утверждение.");
    }

    @Test
    void live_ExpiredMark_DroppedAndNewNoteStartsFreshSince() {
        BlockedCells cells = new BlockedCells(100);
        cells.note(CELL, 0);
        assertTrue(
                cells.live(99).size() == 1,
                "метка жива до конца срока"
                        + " Иначе тест live_ExpiredMark_DroppedAndNewNoteStartsFreshSince перестанет ловить эту поломку. При смене live_ExpiredMark_DroppedAndNewNoteStartsFreshSince проверь это утверждение.");
        assertTrue(
                cells.live(101).isEmpty(),
                "короткая метка пропала: расчистка не копит старые клетки"
                        + " Иначе тест live_ExpiredMark_DroppedAndNewNoteStartsFreshSince перестанет ловить эту поломку. При смене live_ExpiredMark_DroppedAndNewNoteStartsFreshSince проверь это утверждение.");
        cells.note(CELL, 150);
        assertEquals(
                150,
                cells.live(150).get(0).since(),
                "преграда вернулась — метка новая, задание снова может встать"
                        + " Иначе тест live_ExpiredMark_DroppedAndNewNoteStartsFreshSince перестанет ловить эту поломку. При смене live_ExpiredMark_DroppedAndNewNoteStartsFreshSince проверь это утверждение.");
    }

    @Test
    void live_OtherCells_StayApart() {
        BlockedCells cells = new BlockedCells(100);
        cells.note(CELL, 10);
        cells.note(new BlockCoord(11, 64, 5), 10);
        assertEquals(
                2,
                cells.live(10).size(),
                "разные преграды — разные метки: " + cells.live(10)
                        + " Иначе тест live_OtherCells_StayApart перестанет ловить эту поломку. При смене live_OtherCells_StayApart проверь это утверждение.");
    }
}
