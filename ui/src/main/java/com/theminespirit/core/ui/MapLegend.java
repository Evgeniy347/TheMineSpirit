package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record MapLegend(boolean open, int x, int y, boolean pathsVisible, Site dirt, Site sand) {
    public static final String KEY_DIRT = "theminespirit.map.legend.dirt";
    public static final String KEY_SAND = "theminespirit.map.legend.sand";
    public static final String KEY_PATHS = "theminespirit.map.legend.paths";
    public static final String KEY_MISSING = "theminespirit.map.legend.missing";
    public static final String KEY_LOW = "theminespirit.map.legend.low";
    public static final String KEY_EMPTY = "theminespirit.map.legend.empty";
    public static final int PAD = 3;
    public static final int LINE = 11;
    public static final int ICON = 6;
    public static final int GAP = 3;
    public static final int CLOSE = 8;

    public enum Kind {
        DIRT,
        SAND,
        PATHS
    }

    public record Site(boolean found, int percent, boolean low, boolean empty) {
        public static Site missing() {
            return new Site(false, 0, false, false);
        }

        public static Site of(MapQuarry quarry) {
            Objects.requireNonNull(quarry, "Место добычи карты не может быть null");
            return quarry.found() ? new Site(true, quarry.percent(), quarry.low(), quarry.empty()) : missing();
        }
    }

    public record Row(
            Kind kind,
            String titleKey,
            boolean found,
            int percent,
            boolean crossed,
            boolean dimmed,
            String noteKey,
            int color) {
        public Row {
            Objects.requireNonNull(kind, "Вид строки легенды не может быть null");
            Objects.requireNonNull(titleKey, "Название строки легенды не может быть null");
            Objects.requireNonNull(noteKey, "Пометка строки не может быть null: пусто — пустая строка");
        }

        public boolean showsPercent() {
            return KEY_LOW.equals(noteKey);
        }
    }

    @FunctionalInterface
    public interface Text {
        String get(String key, int... args);
    }

    public record Frame(int x, int y, int width, int height, List<RowBox> rows, int closeX, int closeY) {
        public record RowBox(Row row, int x1, int y1, int x2, int y2) {
            public boolean inside(double mouseX, double mouseY) {
                return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
            }
        }

        public Frame {
            rows = List.copyOf(rows);
        }

        public boolean inside(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public boolean closeAt(double mouseX, double mouseY) {
            return mouseX >= closeX && mouseX < closeX + CLOSE && mouseY >= closeY && mouseY < closeY + CLOSE;
        }

        public @Nullable Row rowAt(double mouseX, double mouseY) {
            for (RowBox box : rows) {
                if (box.inside(mouseX, mouseY)) {
                    return box.row();
                }
            }
            return null;
        }

        public static Frame hidden() {
            return new Frame(0, 0, 0, 0, List.of(), 0, 0);
        }
    }

    public MapLegend {
        Objects.requireNonNull(dirt, "Место добычи земли не может быть null: пусто — Site.missing()");
        Objects.requireNonNull(sand, "Место добычи песка не может быть null: пусто — Site.missing()");
        if ((dirt.found() && dirt.percent() < 0) || (sand.found() && sand.percent() < 0)) {
            throw new IllegalArgumentException("Отрицательный остаток места добычи: проценты считаются от 0");
        }
    }

    public static MapLegend initial() {
        return new MapLegend(true, -1, -1, true, Site.missing(), Site.missing());
    }

    public MapLegend withQuarries(List<MapQuarry> quarries) {
        Objects.requireNonNull(quarries, "Места добычи карты не могут быть null: пусто — пустой список");
        Site nextDirt = dirt;
        Site nextSand = sand;
        for (MapQuarry quarry : quarries) {
            switch (quarry.resource()) {
                case "dirt" -> nextDirt = Site.of(quarry);
                case "sand" -> nextSand = Site.of(quarry);
                default -> {}
            }
        }
        return new MapLegend(open, x, y, pathsVisible, nextDirt, nextSand);
    }

    public MapLegend toggledPaths() {
        return new MapLegend(open, x, y, !pathsVisible, dirt, sand);
    }

    public MapLegend opened() {
        return new MapLegend(true, x, y, pathsVisible, dirt, sand);
    }

    public MapLegend closed() {
        return new MapLegend(false, x, y, pathsVisible, dirt, sand);
    }

    public List<Row> rows() {
        return List.of(
                row(Kind.DIRT, KEY_DIRT, dirt, MapStyle.DIRT),
                row(Kind.SAND, KEY_SAND, sand, MapStyle.SAND),
                new Row(Kind.PATHS, KEY_PATHS, true, 0, !pathsVisible, !pathsVisible, "", MapStyle.PATH));
    }

    public Frame frame(MapViewport viewport, MapPaint.Font font, Text text) {
        Objects.requireNonNull(viewport, "Окно карты не может быть null: легенда живёт внутри карты");
        Objects.requireNonNull(font, "Шрифт легенды не может быть null: без него не измерить подписи");
        Objects.requireNonNull(text, "Перевод легенды не может быть null: подписи приходят ключами");
        if (!open) {
            return Frame.hidden();
        }
        List<Row> items = rows();
        List<Frame.RowBox> boxes = new ArrayList<>();
        int content = CLOSE + GAP;
        for (Row row : items) {
            content = Math.max(content, width(row, font, text));
        }
        int naturalWidth = PAD * 2 + ICON + GAP + content;
        int naturalHeight = PAD * 2 + items.size() * LINE;
        int width = Math.min(naturalWidth, viewport.width());
        int height = Math.min(naturalHeight, viewport.height());
        int left = x < 0 ? viewport.left() + viewport.width() - width - PAD : x;
        int top = y < 0 ? viewport.top() + PAD : y;
        int clampedX = clamp(left, viewport.left(), viewport.left() + viewport.width() - width);
        int clampedY = clamp(top, viewport.top(), viewport.top() + viewport.height() - height);
        for (int index = 0; index < items.size(); index++) {
            int rowTop = clampedY + PAD + index * LINE;
            boxes.add(
                    new Frame.RowBox(items.get(index), clampedX + PAD, rowTop, clampedX + width - PAD, rowTop + LINE));
        }
        return new Frame(clampedX, clampedY, width, height, boxes, clampedX + width - PAD - CLOSE, clampedY + PAD);
    }

    public MapLegend moved(int dx, int dy, MapViewport viewport, MapPaint.Font font, Text text) {
        Frame frame = frame(viewport, font, text);
        int width = frame.width();
        int height = frame.height();
        int nextX = clamp(frame.x() + dx, viewport.left(), viewport.left() + viewport.width() - width);
        int nextY = clamp(frame.y() + dy, viewport.top(), viewport.top() + viewport.height() - height);
        return new MapLegend(open, nextX, nextY, pathsVisible, dirt, sand);
    }

    private static Row row(Kind kind, String titleKey, Site site, int color) {
        boolean found = site.found();
        String note = !found ? KEY_MISSING : site.empty() ? KEY_EMPTY : site.low() ? KEY_LOW : "";
        return new Row(kind, titleKey, found, site.percent(), !found || site.empty(), false, note, color);
    }

    private static int width(Row row, MapPaint.Font font, Text text) {
        int width = font.width().applyAsInt(text.get(row.titleKey()));
        if (!row.noteKey().isEmpty()) {
            width += GAP + font.width().applyAsInt(note(row, text));
        }
        return width;
    }

    private static String note(Row row, Text text) {
        return row.showsPercent() ? text.get(row.noteKey(), row.percent()) : text.get(row.noteKey());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }
}
