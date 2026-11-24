package com.theminespirit.core.ui;

public final class CraftLayout {
    public static final int WIDTH = 360;
    public static final int HEIGHT = 256;
    public static final int LIST_X = 8;
    public static final int LIST_Y = 20;
    public static final int SEARCH_Y = 4;
    public static final int SEARCH_HEIGHT = 14;
    public static final int SEARCH_WIDTH = 162;
    public static final int PANEL_X = 184;
    public static final int PANEL_WIDTH = 166;
    public static final int MODE_Y = 30;
    public static final int MODE_GAP = 2;
    public static final int BUTTON_HEIGHT = 14;
    public static final int RAISE_Y = 48;
    public static final int CLEAR_Y = 64;
    public static final int CHAIN_Y = 96;
    public static final int CHAIN_HEIGHT = 100;
    public static final int CRAFT_Y = 200;
    public static final int MAX_Y = 216;
    public static final int REASON_Y = 232;

    private CraftLayout() {}

    public static BoardLayout.Rect search() {
        return new BoardLayout.Rect(LIST_X, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT);
    }

    public static BoardLayout.Rect modeTab(int index) {
        if (index < 0 || index > 2) {
            throw new IllegalArgumentException("Вкладка режима " + index + ": их три — склад, рецепты и заказы");
        }
        int width = (PANEL_WIDTH - 2 * MODE_GAP) / 3;
        return new BoardLayout.Rect(PANEL_X + index * (width + MODE_GAP), MODE_Y, width, BUTTON_HEIGHT);
    }

    public static BoardLayout.Rect button(int row) {
        return new BoardLayout.Rect(PANEL_X, row, PANEL_WIDTH, BUTTON_HEIGHT);
    }

    public static BoardLayout.Rect panel() {
        return new BoardLayout.Rect(PANEL_X, CHAIN_Y, PANEL_WIDTH, CHAIN_HEIGHT);
    }

    public static BoardLayout.Rect sideColumn() {
        return new BoardLayout.Rect(PANEL_X, MODE_Y, PANEL_WIDTH, Math.max(1, HEIGHT - MODE_Y - 8));
    }

    public static BoardLayout.Rect guiArea(int guiLeft, int guiTop) {
        BoardLayout.Rect column = sideColumn();
        return new BoardLayout.Rect(guiLeft + column.x(), guiTop + column.y(), column.width(), column.height());
    }
}
