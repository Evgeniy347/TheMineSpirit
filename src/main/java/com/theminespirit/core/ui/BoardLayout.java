package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.List;

/** Раскладка экрана доски задач: окно, кнопки фильтров, три равные колонки, заголовки и плитки. */
public final class BoardLayout {
    public static final int MARGIN = 8;
    public static final int STAGE_HEIGHT = 12;
    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_GAP = 4;
    public static final int BUTTON_MAX_WIDTH = 150;
    public static final int BORDER_LEFT = 9;
    public static final int BORDER_TOP = 18;
    public static final int BORDER_RIGHT = 9;
    public static final int BORDER_BOTTOM = 9;
    public static final int COLUMN_COUNT = 3;
    public static final int COLUMN_GAP = 4;
    public static final int COLUMN_HEADER = 12;
    public static final int CONTENT_PAD = 2;
    public static final int TILE_HEIGHT = 32;
    public static final int TILE_GAP = 2;

    private final Rect window;
    private final Rect inner;
    private final Rect list;
    private final List<Rect> columns;
    private final List<Rect> headers;
    private final Rect statusButton;
    private final Rect spiritButton;

    private BoardLayout(
            Rect window,
            Rect inner,
            Rect list,
            List<Rect> columns,
            List<Rect> headers,
            Rect statusButton,
            Rect spiritButton) {
        this.window = window;
        this.inner = inner;
        this.list = list;
        this.columns = List.copyOf(columns);
        this.headers = List.copyOf(headers);
        this.statusButton = statusButton;
        this.spiritButton = spiritButton;
    }

    public static BoardLayout of(int screenWidth, int screenHeight) {
        int windowX = MARGIN;
        int buttonY = MARGIN + STAGE_HEIGHT + BUTTON_GAP;
        int windowY = buttonY + BUTTON_HEIGHT + BUTTON_GAP;
        int windowWidth = Math.max(0, screenWidth - 2 * MARGIN);
        int windowHeight = Math.max(0, screenHeight - windowY - MARGIN);
        Rect window = new Rect(windowX, windowY, windowWidth, windowHeight);
        Rect inner = new Rect(
                window.x() + BORDER_LEFT,
                window.y() + BORDER_TOP,
                Math.max(0, window.width() - BORDER_LEFT - BORDER_RIGHT),
                Math.max(0, window.height() - BORDER_TOP - BORDER_BOTTOM));
        int columnsTop = inner.y() + CONTENT_PAD;
        int listTop = columnsTop + COLUMN_HEADER + CONTENT_PAD;
        int listHeight = Math.max(0, inner.bottom() - CONTENT_PAD - listTop);
        Rect list = new Rect(inner.x(), listTop, inner.width(), listHeight);
        int columnWidth = Math.max(1, (inner.width() - (COLUMN_COUNT - 1) * COLUMN_GAP) / COLUMN_COUNT);
        List<Rect> columns = new ArrayList<>(COLUMN_COUNT);
        List<Rect> headers = new ArrayList<>(COLUMN_COUNT);
        for (int i = 0; i < COLUMN_COUNT; i++) {
            int x = inner.x() + i * (columnWidth + COLUMN_GAP);
            columns.add(new Rect(x, listTop, columnWidth, listHeight));
            headers.add(new Rect(x, columnsTop, columnWidth, COLUMN_HEADER));
        }
        int buttonWidth = Math.min(BUTTON_MAX_WIDTH, Math.max(60, (inner.width() - BUTTON_GAP) / 2));
        int buttonX = inner.x() + CONTENT_PAD;
        Rect statusButton = new Rect(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT);
        Rect spiritButton = new Rect(buttonX + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        return new BoardLayout(window, inner, list, columns, headers, statusButton, spiritButton);
    }

    public Rect window() {
        return window;
    }

    public Rect inner() {
        return inner;
    }

    public Rect list() {
        return list;
    }

    public List<Rect> columns() {
        return columns;
    }

    public List<Rect> headers() {
        return headers;
    }

    public Rect stage() {
        return new Rect(window.x(), MARGIN, window.width(), STAGE_HEIGHT);
    }

    public Rect statusButton() {
        return statusButton;
    }

    public Rect spiritButton() {
        return spiritButton;
    }

    public Rect tile(int column, int index) {
        Rect area = column(column);
        if (index < 0) {
            throw new IllegalArgumentException("Номер плитки не может быть отрицательным: " + index);
        }
        return new Rect(area.x(), list.y() + index * (TILE_HEIGHT + TILE_GAP), area.width(), TILE_HEIGHT);
    }

    public int columnAt(double mouseX, double mouseY) {
        if (!list.contains((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            Rect column = columns.get(i);
            if (mouseX >= column.x() && mouseX < column.right()) {
                return i;
            }
        }
        return -1;
    }

    private Rect column(int column) {
        if (column < 0 || column >= columns.size()) {
            throw new IndexOutOfBoundsException("Колонки " + column + " нет: их всего " + columns.size());
        }
        return columns.get(column);
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException(
                        "Ширина и высота прямоугольника не могут быть отрицательными: " + width + "x" + height);
            }
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
}
