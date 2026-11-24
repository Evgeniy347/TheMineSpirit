package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BoardLayoutTests {
    @ParameterizedTest(name = "{0}x{1}: {2} колонок")
    @CsvSource({"960, 540, 4", "640, 360, 2", "500, 360, 1"})
    void columns_ScreenWidthDiffers_DependOnWidth(int screenWidth, int screenHeight, int expected) {
        List<BoardTileGroup> groups = expected == 1 ? List.of(BoardTileGroup.DONE) : List.of(BoardTileGroup.values());
        BoardLayout layout = BoardLayout.of(screenWidth, screenHeight, groups);
        assertEquals(
                expected,
                BoardLayout.columnsFor(screenWidth),
                "Число колонок доски обязано зависеть от ширины: на узком GUI четыре колонки не читаются, "
                        + "а одна колонка на широком оставляет пустоту"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене columns_ScreenWidthDiffers_DependOnWidth проверь это утверждение.");
        assertEquals(
                groups.size(),
                layout.slotCount(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене columns_ScreenWidthDiffers_DependOnWidth проверь это утверждение.");
        int last = groups.size() - 1;
        assertEquals(
                expected > last,
                layout.slots().get(last).header().y()
                        == layout.slots().get(0).header().y(),
                "Раскладка обязана ставить группы в " + expected + " колонок: последняя группа на том же ряду, "
                        + "только если колонок хватает"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене columns_ScreenWidthDiffers_DependOnWidth проверь это утверждение.");
    }

    @Test
    void slots_WideGui_StandInOneRowAndDoNotOverlap() {
        BoardLayout layout = BoardLayout.of(960, 540);
        int previousRight = -1;
        for (BoardLayout.Slot slot : layout.slots()) {
            assertTrue(
                    slot.header().x() >= layout.window().x()
                            && slot.header().right() <= layout.window().right(),
                    "Колонка " + slot.group() + " обязана помещаться в окно доски"
                            + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене slots_WideGui_StandInOneRowAndDoNotOverlap проверь это утверждение.");
            assertTrue(
                    slot.list().bottom() <= layout.window().bottom(),
                    "Список " + slot.group() + " не должен вылезать под подвал"
                            + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене slots_WideGui_StandInOneRowAndDoNotOverlap проверь это утверждение.");
            if (previousRight >= 0) {
                assertTrue(
                        previousRight <= slot.header().x(),
                        "Колонки обязаны не перекрываться"
                                + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене slots_WideGui_StandInOneRowAndDoNotOverlap проверь это утверждение.");
            }
            previousRight = slot.header().right();
        }
    }

    @Test
    void slots_NarrowGui_StackInTwoRows() {
        BoardLayout layout = BoardLayout.of(640, 360);
        List<BoardLayout.Slot> slots = layout.slots();
        assertEquals(
                4,
                slots.size(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене slots_NarrowGui_StackInTwoRows проверь это утверждение.");
        assertTrue(
                slots.get(0).header().bottom() <= slots.get(2).header().y(),
                "На узком GUI вторая пара групп обязана идти ниже первой, иначе заголовки наложатся"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене slots_NarrowGui_StackInTwoRows проверь это утверждение.");
        for (BoardLayout.Slot slot : slots) {
            assertTrue(
                    slot.list().height() > 0,
                    "У группы " + slot.group() + " обязан быть видимый список"
                            + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене slots_NarrowGui_StackInTwoRows проверь это утверждение.");
            assertTrue(
                    slot.list().bottom() <= layout.window().bottom(),
                    "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене slots_NarrowGui_StackInTwoRows проверь это утверждение.");
        }
    }

    @Test
    void tiles_LongList_StayInsideTheirList() {
        BoardLayout layout = BoardLayout.of(960, 540);
        BoardLayout.Rect list = layout.list(0);
        for (int index = 0; index < 3; index++) {
            BoardLayout.Rect tile = layout.tile(0, index);
            assertEquals(
                    list.x(),
                    tile.x(),
                    "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене tiles_LongList_StayInsideTheirList проверь это утверждение.");
            assertEquals(
                    list.width(),
                    tile.width(),
                    "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене tiles_LongList_StayInsideTheirList проверь это утверждение.");
            assertTrue(
                    tile.bottom() <= list.bottom(),
                    "Плитка " + index + " обязана попадать в область списка"
                            + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене tiles_LongList_StayInsideTheirList проверь это утверждение.");
        }
    }

    @Test
    void dock_WideGui_AppearsOnlyOnWideGui() {
        BoardLayout wide = BoardLayout.of(960, 540);
        assertTrue(
                wide.hasDock(),
                "На 960 у доски обязан быть правый док с выбранным заданием"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене dock_WideGui_AppearsOnlyOnWideGui проверь это утверждение.");
        assertEquals(
                BoardLayout.DOCK_WIDTH,
                wide.dock().width(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене dock_WideGui_AppearsOnlyOnWideGui проверь это утверждение.");
        assertEquals(
                wide.dock().x() - BoardLayout.DOCK_GAP,
                wide.window().right(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене dock_WideGui_AppearsOnlyOnWideGui проверь это утверждение.");
        assertFalse(
                BoardLayout.of(640, 360).hasDock(),
                "На 640 док не помещается и не рисуется"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене dock_WideGui_AppearsOnlyOnWideGui проверь это утверждение.");
    }

    @Test
    void toolbar_AnyGui_SitsAboveWindow() {
        BoardLayout layout = BoardLayout.of(960, 540);
        assertTrue(
                layout.toolbar().bottom() <= layout.window().y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене toolbar_AnyGui_SitsAboveWindow проверь это утверждение.");
        assertTrue(
                layout.toolbar().bottom() <= layout.dock().y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене toolbar_AnyGui_SitsAboveWindow проверь это утверждение.");
    }

    @Test
    void slotAt_MouseInsideList_FindsColumn() {
        BoardLayout layout = BoardLayout.of(960, 540);
        BoardLayout.Rect list = layout.list(1);
        assertEquals(
                1,
                layout.slotAt(list.x() + 1, list.y() + 1),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене slotAt_MouseInsideList_FindsColumn проверь это утверждение.");
        assertEquals(
                -1,
                layout.slotAt(layout.toolbar().x(), layout.toolbar().y()),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене slotAt_MouseInsideList_FindsColumn проверь это утверждение.");
    }
}
