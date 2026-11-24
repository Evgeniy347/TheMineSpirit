package com.theminespirit.core.ui;

import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BoardLayout {
    public static final int MARGIN = 8;
    public static final int COLUMN_COUNT = 4;
    public static final int WIDE_MIN_WIDTH = 820;
    public static final int SINGLE_MIN_WIDTH = 560;
    public static final int DOCK_WIDTH = 208;
    public static final int DOCK_GAP = 8;
    public static final int COLUMN_GAP = 6;
    public static final int COLUMN_HEADER = 18;
    public static final int CONTENT_PAD = 2;
    public static final int TILE_HEIGHT = 54;
    public static final int TILE_GAP = 4;
    public static final int SCROLLBAR_WIDTH = 3;
    public static final int MATERIAL_ICON = 16;

    public static int materialChipWidth(int labelWidth) {
        if (labelWidth < 0) {
            throw new IllegalArgumentException(
                    "Ширина подписи " + labelWidth + " отрицательна: чип материала не бывает уже значка");
        }
        return MATERIAL_ICON + 7 + labelWidth;
    }

    private final int screenWidth;
    private final BoardLayout.Rect toolbar;
    private final BoardLayout.Rect window;
    private final BoardLayout.Rect dock;
    private final List<Slot> slots;

    private BoardLayout(int screenWidth, Rect toolbar, Rect window, Rect dock, List<Slot> slots) {
        this.screenWidth = screenWidth;
        this.toolbar = toolbar;
        this.window = window;
        this.dock = dock;
        this.slots = List.copyOf(slots);
    }

    public record Slot(BoardTileGroup group, Rect header, Rect list) {
        public Slot {
            Objects.requireNonNull(group, "Группа слота не может быть null: по ней подписан заголовок колонки");
            Objects.requireNonNull(header, "Заголовок слота не может быть null: группа без подписи непонятна");
            Objects.requireNonNull(list, "Список слота не может быть null: плиткам нужно место");
        }

        public Rect scrollbar() {
            int x = list.right() - SCROLLBAR_WIDTH;
            return new Rect(x, list.y() + 2, SCROLLBAR_WIDTH, Math.max(0, list.height() - 4));
        }
    }

    public static int columnsFor(int screenWidth) {
        if (screenWidth >= WIDE_MIN_WIDTH) {
            return COLUMN_COUNT;
        }
        return screenWidth >= SINGLE_MIN_WIDTH ? 2 : 1;
    }

    public static BoardLayout of(int screenWidth, int screenHeight) {
        return of(screenWidth, screenHeight, List.of(BoardTileGroup.values()));
    }

    public static BoardLayout of(int screenWidth, int screenHeight, List<BoardTileGroup> groups) {
        Objects.requireNonNull(groups, "Список групп не может быть null: пустую доску задают пустым списком");
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("У доски нет ни одной группы: рисовать нечего");
        }
        ShellLayout shell = ShellLayout.of(screenWidth, screenHeight);
        Rect toolbar = shell.toolbar();
        Rect body = shell.body();
        int dockWidth = screenWidth >= WIDE_MIN_WIDTH ? Math.min(DOCK_WIDTH, body.width()) : 0;
        int columnsWidth = Math.max(0, body.width() - (dockWidth > 0 ? dockWidth + DOCK_GAP : 0));
        Rect window = new Rect(body.x(), body.y(), columnsWidth, body.height());
        Rect dock = dockWidth > 0
                ? new Rect(body.x() + body.width() - dockWidth, body.y(), dockWidth, body.height())
                : new Rect(0, 0, 0, 0);
        int columns = columnsFor(screenWidth);
        int rows = (groups.size() + columns - 1) / columns;
        int cellHeight = rows == 0 ? 0 : Math.max(0, (window.height() - (rows - 1) * COLUMN_GAP) / rows);
        int columnWidth = Math.max(1, (window.width() - (columns - 1) * COLUMN_GAP) / columns);
        List<Slot> slots = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int x = window.x() + column * (columnWidth + COLUMN_GAP);
            int y = window.y() + row * (cellHeight + COLUMN_GAP);
            Rect header = new Rect(x, y, columnWidth, Math.min(COLUMN_HEADER, cellHeight));
            Rect list = new Rect(
                    x,
                    header.bottom() + CONTENT_PAD,
                    columnWidth,
                    Math.max(0, cellHeight - header.height() - CONTENT_PAD));
            slots.add(new Slot(groups.get(i), header, list));
        }
        return new BoardLayout(screenWidth, toolbar, window, dock, slots);
    }

    public int screenWidth() {
        return screenWidth;
    }

    public Rect toolbar() {
        return toolbar;
    }

    public Rect window() {
        return window;
    }

    public Rect dock() {
        return dock;
    }

    public boolean hasDock() {
        return dock.width() > 0;
    }

    public List<Slot> slots() {
        return slots;
    }

    public int slotCount() {
        return slots.size();
    }

    public Rect header(int slot) {
        return slot(slot).header();
    }

    public Rect list(int slot) {
        return slot(slot).list();
    }

    public Rect tile(int slot, int index) {
        Rect area = list(slot);
        if (index < 0) {
            throw new IllegalArgumentException("Номер плитки не может быть отрицательным: " + index);
        }
        return new Rect(area.x(), area.y() + index * (TILE_HEIGHT + TILE_GAP), area.width(), TILE_HEIGHT);
    }

    public int slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).list().contains((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
                return i;
            }
        }
        return -1;
    }

    private Slot slot(int slot) {
        if (slot < 0 || slot >= slots.size()) {
            throw new IndexOutOfBoundsException("Слота " + slot + " нет: их всего " + slots.size());
        }
        return slots.get(slot);
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
