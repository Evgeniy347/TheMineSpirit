package com.theminespirit.core.ui;

import java.util.Objects;

public record BoardDock(BoardLayout.Rect content, BoardLayout.Rect mapButton, BoardLayout.Rect treeButton) {
    public static final int PAD = 10;
    public static final int TOP = 9;
    public static final int BUTTON_HEIGHT = 16;
    public static final int BUTTON_GAP = 6;
    public static final int BUTTONS_BOTTOM_INSET = 26;
    public static final int MIN_BUTTON_WIDTH = 48;

    public BoardDock {
        Objects.requireNonNull(content, "Область содержимого дока не может быть null: в неё пишут задание");
        Objects.requireNonNull(mapButton, "Кнопка карты не может быть null: без неё не открыть план");
        Objects.requireNonNull(treeButton, "Кнопка древа не может быть null: без неё не открыть древо");
    }

    public static BoardDock of(BoardLayout.Rect panel) {
        Objects.requireNonNull(panel, "Панель дока не может быть null: док рисуется только при известной панели");
        int width = panel.width() - 2 * PAD;
        int buttonWidth = (width - BUTTON_GAP) / 2;
        if (buttonWidth < MIN_BUTTON_WIDTH) {
            throw new IllegalArgumentException("Док шириной " + panel.width() + ": двум кнопкам меньше "
                    + MIN_BUTTON_WIDTH + " px — подписи «На карте» и «В древе» не влезут");
        }
        int buttonsY = panel.bottom() - BUTTONS_BOTTOM_INSET;
        int contentHeight = Math.max(0, buttonsY - panel.y() - TOP - 4);
        return new BoardDock(
                new BoardLayout.Rect(panel.x() + PAD, panel.y() + TOP, width, contentHeight),
                new BoardLayout.Rect(panel.x() + PAD, buttonsY, buttonWidth, BUTTON_HEIGHT),
                new BoardLayout.Rect(panel.x() + PAD + buttonWidth + BUTTON_GAP, buttonsY, buttonWidth, BUTTON_HEIGHT));
    }
}
