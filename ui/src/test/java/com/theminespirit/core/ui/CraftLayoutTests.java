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
    void modeTab_OutOfRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CraftLayout.modeTab(3),
                "Четвёртая вкладка обязана падать: режимов ровно три, иначе лишний индекс спрячет ошибку вызова. При смене modeTab_OutOfRange_IsRejected проверь это утверждение.");
    }
}
