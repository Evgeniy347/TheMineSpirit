package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.ui.BoardLayout.Rect;
import org.junit.jupiter.api.Test;

class TreeDockTests {
    @Test
    void dock_DefaultPanel_KeepsLegendAtBottom() {
        Rect panel = new Rect(716, 40, 236, 420);
        TreeDock dock = TreeDock.of(panel);
        assertTrue(
                dock.legend().bottom() <= panel.bottom(),
                "Легенда обязана оставаться внутри дока: иначе последняя строка уезжает за подвал экрана и её не видно. При смене dock_DefaultPanel_KeepsLegendAtBottom проверь это утверждение.");
        assertTrue(
                dock.content().bottom() <= dock.legend().y(),
                "Карточка узла обязана заканчиваться выше легенды: иначе список объектов наедет на пояснение состояний. При смене dock_DefaultPanel_KeepsLegendAtBottom проверь это утверждение.");
    }

    @Test
    void dock_LegendWidth_MatchesContent() {
        Rect panel = new Rect(716, 40, 236, 420);
        TreeDock dock = TreeDock.of(panel);
        assertEquals(
                dock.content().width(),
                dock.legend().width(),
                "Легенда и содержимое дока делят одну ширину: иначе обрезанный текст легенды не совпадёт с полями карточки. При смене dock_LegendWidth_MatchesContent проверь это утверждение.");
    }

    @Test
    void rows_ShortPanel_CountsVisibleRows() {
        Rect panel = new Rect(716, 40, 236, 80);
        TreeDock dock = TreeDock.of(panel);
        assertEquals(
                0,
                dock.rows(panel.y() + 70),
                "При нехватке места список объектов обязан показать ноль строк: иначе строки полезут под легенду. При смене rows_ShortPanel_CountsVisibleRows проверь это утверждение.");
    }

    @Test
    void dock_NarrowPanel_IsRejected() {
        Rect panel = new Rect(700, 40, 30, 420);
        assertThrows(
                IllegalArgumentException.class,
                () -> TreeDock.of(panel),
                "Узкий док обязан падать: карточка и список в 30 px не читаются, иначе тихое переполнение скроет ошибку раскладки. При смене dock_NarrowPanel_IsRejected проверь это утверждение.");
    }
}
