package com.theminespirit.core.ui;

import java.util.Objects;

public record TreeDock(BoardLayout.Rect content, BoardLayout.Rect legend, int rowHeight) {
    public static final int PAD = 10;
    public static final int TOP = 8;
    public static final int ROW_HEIGHT = 18;
    public static final int LEGEND_HEIGHT = 11;
    public static final int LEGEND_GAP = 4;

    public TreeDock {
        Objects.requireNonNull(
                content, "Область содержимого дока не может быть null: карточка узла обязана где-то жить");
        Objects.requireNonNull(legend, "Строка легенды не может быть null: без неё состояния узлов непонятны");
        if (rowHeight < 1) {
            throw new IllegalArgumentException("Высота строки списка " + rowHeight + ": строка хотя бы в пиксель");
        }
    }

    public static TreeDock of(BoardLayout.Rect panel) {
        Objects.requireNonNull(panel, "Панель дока не может быть null: док рисуется только при известной панели");
        int x = panel.x() + PAD;
        int width = panel.width() - 2 * PAD;
        if (width < 40) {
            throw new IllegalArgumentException(
                    "Док шириной " + panel.width() + ": карточке узла и списку объектов нужно хотя бы 40 px");
        }
        BoardLayout.Rect legend = new BoardLayout.Rect(x, panel.bottom() - LEGEND_HEIGHT - 3, width, LEGEND_HEIGHT);
        int contentHeight = Math.max(0, legend.y() - LEGEND_GAP - (panel.y() + TOP));
        return new TreeDock(new BoardLayout.Rect(x, panel.y() + TOP, width, contentHeight), legend, ROW_HEIGHT);
    }

    public int rows(int listTop) {
        int available = legend.y() - LEGEND_GAP - listTop;
        return Math.max(0, available / rowHeight);
    }
}
