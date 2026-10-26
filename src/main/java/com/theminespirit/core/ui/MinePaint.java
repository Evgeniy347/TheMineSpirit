package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MinePaint {
    public static final int BORDER = 2;
    private static final int LABEL_PAD = 2;

    private MinePaint() {}

    public static List<MapPaint.Shape> of(
            MineSnapshot snapshot, MapViewport viewport, Optional<MineSnapshot.Rect> hovered, MapPaint.Font font) {
        Objects.requireNonNull(snapshot, "Срез шахты не может быть null");
        Objects.requireNonNull(viewport, "Окно карты не может быть null");
        Objects.requireNonNull(hovered, "Наведение постройки не может быть null: пусто — Optional.empty()");
        List<MapPaint.Shape> out = new ArrayList<>();
        out.add(new MapPaint.Fill(
                viewport.left(),
                viewport.top(),
                viewport.left() + viewport.width(),
                viewport.top() + viewport.height(),
                MineStyle.BACKGROUND));
        for (MineSnapshot.Cell cell : snapshot.cells()) {
            box(viewport, cell.x(), cell.z(), cell.x() + 1, cell.z() + 1)
                    .ifPresent(box -> out.add(
                            new MapPaint.Fill(box.x1(), box.y1(), box.x2(), box.y2(), MineStyle.fill(cell.kind()))));
        }
        List<MapPaint.Text> labels = new ArrayList<>();
        for (MineSnapshot.Rect rect : snapshot.rects()) {
            box(viewport, rect.x1(), rect.z1(), rect.x2() + 1.0, rect.z2() + 1.0)
                    .ifPresent(
                            box -> border(out, box, hovered.filter(rect::equals).isPresent()));
            label(labels, viewport, rect, font);
        }
        out.addAll(labels);
        return out;
    }

    private static void border(List<MapPaint.Shape> out, Box box, boolean hovered) {
        int color = hovered ? MineStyle.HOVER_BORDER : MineStyle.RECT_BORDER;
        out.add(new MapPaint.Fill(box.x1(), box.y1(), box.x2(), Math.min(box.y2(), box.y1() + BORDER), color));
        out.add(new MapPaint.Fill(box.x1(), Math.max(box.y1(), box.y2() - BORDER), box.x2(), box.y2(), color));
        out.add(new MapPaint.Fill(box.x1(), box.y1(), Math.min(box.x2(), box.x1() + BORDER), box.y2(), color));
        out.add(new MapPaint.Fill(Math.max(box.x1(), box.x2() - BORDER), box.y1(), box.x2(), box.y2(), color));
    }

    private static void label(
            List<MapPaint.Text> labels, MapViewport viewport, MineSnapshot.Rect rect, MapPaint.Font font) {
        box(viewport, rect.x1(), rect.z1(), rect.x2() + 1.0, rect.z2() + 1.0)
                .filter(box -> box.x2() - box.x1() >= font.width().applyAsInt(rect.label()) + 2 * LABEL_PAD)
                .filter(box -> box.y2() - box.y1() >= font.lineHeight() + 2 * LABEL_PAD)
                .ifPresent(box -> labels.add(
                        new MapPaint.Text(box.x1() + LABEL_PAD, box.y1() + LABEL_PAD, rect.label(), MineStyle.TEXT)));
    }

    private static Optional<Box> box(MapViewport viewport, double x1, double z1, double x2, double z2) {
        double sx1 = Math.round(viewport.screenX(x1));
        double sy1 = Math.round(viewport.screenY(z1));
        double sx2 = Math.round(viewport.screenX(x2));
        double sy2 = Math.round(viewport.screenY(z2));
        int right = viewport.left() + viewport.width();
        int bottom = viewport.top() + viewport.height();
        if (sx2 <= viewport.left() || sx1 >= right || sy2 <= viewport.top() || sy1 >= bottom) {
            return Optional.empty();
        }
        int left = (int) Math.max(viewport.left(), sx1);
        int top = (int) Math.max(viewport.top(), sy1);
        return Optional.of(new Box(left, top, (int) Math.min(right, Math.max(sx2, left + 1)), (int)
                Math.min(bottom, Math.max(sy2, top + 1))));
    }

    private record Box(int x1, int y1, int x2, int y2) {}
}
