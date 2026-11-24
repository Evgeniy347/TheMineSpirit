package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.ui.BoardLayout.Rect;
import org.junit.jupiter.api.Test;

class BoardDockTests {
    @Test
    void dock_DefaultPanel_PlacesTwoButtonsSideBySide() {
        Rect panel = new Rect(752, 40, BoardLayout.DOCK_WIDTH, 420);
        BoardDock dock = BoardDock.of(panel);
        assertTrue(
                dock.mapButton().right() <= dock.treeButton().x(),
                "Кнопки карты и древа стоят в один ряд без нахлёста: иначе вторая кнопка перекрывает первую и клик уходит не туда. При смене dock_DefaultPanel_PlacesTwoButtonsSideBySide проверь это утверждение.");
        assertTrue(
                dock.treeButton().right() <= panel.right(),
                "Кнопки обязаны помещаться в панель дока: иначе подпись «В древе» обрежется краем экрана. При смене dock_DefaultPanel_PlacesTwoButtonsSideBySide проверь это утверждение.");
    }

    @Test
    void dock_Content_StaysAboveButtons() {
        Rect panel = new Rect(752, 40, BoardLayout.DOCK_WIDTH, 420);
        BoardDock dock = BoardDock.of(panel);
        assertTrue(
                dock.content().bottom() <= dock.mapButton().y(),
                "Текст задания обязан заканчиваться выше кнопок: иначе строки материалов лягут под кнопки и станут нечитаемы. При смене dock_Content_StaysAboveButtons проверь это утверждение.");
        assertEquals(
                panel.x() + BoardDock.PAD,
                dock.content().x(),
                "Содержимое дока начинается с единого отступа: иначе заголовок и строки съезжают друг относительно друга. При смене dock_Content_StaysAboveButtons проверь это утверждение.");
    }

    @Test
    void dock_NarrowPanel_IsRejected() {
        Rect panel = new Rect(700, 40, 100, 420);
        assertThrows(
                IllegalArgumentException.class,
                () -> BoardDock.of(panel),
                "Узкая панель обязана падать: две кнопки с подписями в 100 px не влезают, иначе молчаливое переполнение спрячет баг раскладки. При смене dock_NarrowPanel_IsRejected проверь это утверждение.");
    }
}
