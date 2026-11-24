package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftLayoutTests {
    @Test
    void layout_FullWindow_FitsPanelInsideWindow() {
        assertTrue(
                CraftLayout.PANEL_X + CraftLayout.PANEL_WIDTH <= CraftLayout.WIDTH,
                "Правая панель обязана помещаться в окно крафта: иначе кнопки «Сделать» уезжают за фон и клики пропадают. При смене layout_FullWindow_FitsPanelInsideWindow проверь это утверждение.");
        assertTrue(
                CraftLayout.MAX_Y + CraftLayout.BUTTON_HEIGHT <= CraftLayout.HEIGHT,
                "Нижняя кнопка обязана помещаться в высоту окна: иначе «Сделать максимум» обрежется подвалом. При смене layout_FullWindow_FitsPanelInsideWindow проверь это утверждение.");
    }

    @Test
    void layout_LeftColumn_DoesNotOverlapPanel() {
        assertTrue(
                CraftLayout.LIST_X + CraftLayout.SEARCH_WIDTH <= CraftLayout.PANEL_X,
                "Поиск и склад не должны заезжать на правую панель: иначе поле поиска перекроет вкладки режимов. При смене layout_LeftColumn_DoesNotOverlapPanel проверь это утверждение.");
        assertEquals(
                CraftLayout.SEARCH_WIDTH,
                CraftLayout.search().width(),
                "Поле поиска занимает всю ширину левой колонки: иначе подсказка «Поиск: доски, кирка…» обрежется раньше срока. При смене layout_LeftColumn_DoesNotOverlapPanel проверь это утверждение.");
    }

    @Test
    void layout_ThreeModeTabs_FitPanelWidth() {
        int previousRight = -1;
        for (int index = 0; index < 3; index++) {
            BoardLayout.Rect tab = CraftLayout.modeTab(index);
            assertTrue(
                    tab.x() >= CraftLayout.PANEL_X && tab.right() <= CraftLayout.PANEL_X + CraftLayout.PANEL_WIDTH,
                    "Вкладка режима " + index
                            + " обязана лежать в панели: иначе третья вкладка «Заказы» вылезет за окно. При смене layout_ThreeModeTabs_FitPanelWidth проверь это утверждение.");
            if (previousRight >= 0) {
                assertTrue(
                        previousRight <= tab.x(),
                        "Вкладки режимов не перекрываются: иначе клик попадает в соседний режим. При смене layout_ThreeModeTabs_FitPanelWidth проверь это утверждение.");
            }
            previousRight = tab.right();
        }
    }

    @Test
    void layout_WideWindow_SymmetricOffset() {
        int vanilla = 176;
        int shift = (CraftLayout.WIDTH - vanilla) / 2;
        assertEquals(
                CraftLayout.WIDTH - vanilla,
                shift * 2,
                "Окно крафта должно расширяться симметрично вокруг ванильного: иначе нечётный запас сдвинет leftPos на полупиксель и клики по слотам уедут. При смене layout_WideWindow_SymmetricOffset проверь это утверждение.");
        assertTrue(
                CraftLayout.PANEL_X >= vanilla,
                "Правая панель начинается правее ванильных 176 px: иначе сдвиг leftPos накроет слоты склада и меню. При смене layout_WideWindow_SymmetricOffset проверь это утверждение.");
    }

    @Test
    void guiArea_ScreenOffset_MovesWithWindow() {
        BoardLayout.Rect area = CraftLayout.guiArea(40, 50);
        assertEquals(
                CraftLayout.PANEL_X + 40,
                area.x(),
                "Зона JEI смещается вместе с окном: иначе рецепты рисуются не над панелью, а где попало. При смене guiArea_ScreenOffset_MovesWithWindow проверь это утверждение.");
        assertEquals(
                CraftLayout.MODE_Y + 50,
                area.y(),
                "Верх зоны JEI — строка вкладок режимов: иначе подсказка рецепта накроет первую вкладку. При смене guiArea_ScreenOffset_MovesWithWindow проверь это утверждение.");
        assertTrue(
                area.bottom() <= 50 + CraftLayout.HEIGHT,
                "Зона JEI не вылезает за окно крафта: иначе она перекроет шапку каркаса. При смене guiArea_ScreenOffset_MovesWithWindow проверь это утверждение.");
    }

    @Test
    void modeTab_OutOfRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CraftLayout.modeTab(3),
                "Четвёртая вкладка обязана падать: режимов ровно три, иначе лишний индекс спрячет ошибку вызова. При смене modeTab_OutOfRange_IsRejected проверь это утверждение.");
    }
}
