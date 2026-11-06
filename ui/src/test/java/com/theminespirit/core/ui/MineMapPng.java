package com.theminespirit.core.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;

public final class MineMapPng {
    public static final Path OUT = Path.of("build", "map-screens");
    public static final int WIDTH = 960;
    public static final int HEIGHT = 640;

    private MineMapPng() {}

    public static MapPaint.Font font() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        FontMetrics metrics = g.getFontMetrics();
        MapPaint.Font font = new MapPaint.Font(metrics::stringWidth, 9);
        g.dispose();
        return font;
    }

    public static void draw(
            MineSnapshot snapshot,
            MapViewport viewport,
            Optional<MineSnapshot.Rect> hovered,
            Optional<MapMarker> hoveredMarker,
            String file)
            throws IOException {
        Files.createDirectories(OUT);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        FontMetrics metrics = g.getFontMetrics();
        MapPaint.Font font = font();
        for (MapPaint.Shape shape : MinePaint.of(snapshot, viewport, hovered, font)) {
            draw(g, metrics, shape);
        }
        for (MapPaint.Shape shape : MapMarkers.paint(snapshot.markers(), viewport, hoveredMarker, font)) {
            draw(g, metrics, shape);
        }
        hoveredMenu(g, metrics, hovered, viewport);
        hoveredMarker.ifPresent(marker -> tooltip(
                g,
                metrics,
                marker.name(),
                "Игрок",
                viewport.screenX(marker.x() + 0.5),
                viewport.screenY(marker.z() + 0.5)));
        g.dispose();
        ImageIO.write(image, "png", OUT.resolve(file).toFile());
    }

    public static MineSnapshot marked(int y) {
        return MineFixtures.snapshot(y).withMarkers(MineFixtures.markers());
    }

    private static void hoveredMenu(
            Graphics2D g, FontMetrics metrics, Optional<MineSnapshot.Rect> hovered, MapViewport viewport) {
        hovered.ifPresent(rect -> tooltip(
                g,
                metrics,
                rect.label(),
                "Готово: " + rect.percent() + "%",
                viewport.screenX((rect.x1() + rect.x2() + 1) / 2.0),
                viewport.screenY((rect.z1() + rect.z2() + 1) / 2.0)));
    }

    private static void draw(Graphics2D g, FontMetrics metrics, MapPaint.Shape shape) {
        switch (shape) {
            case MapPaint.Fill fill -> {
                g.setColor(new Color(fill.color(), true));
                g.fillRect(fill.x1(), fill.y1(), fill.x2() - fill.x1(), fill.y2() - fill.y1());
            }
            case MapPaint.Text text -> {
                g.setColor(new Color(text.color(), true));
                g.drawString(text.text(), text.x(), text.y() + metrics.getAscent() - 2);
            }
            case MapPaint.Raster ignored -> {}
        }
    }

    private static void tooltip(Graphics2D g, FontMetrics metrics, String title, String detail, double x, double y) {
        int pad = 4;
        int width = Math.max(metrics.stringWidth(title), metrics.stringWidth(detail)) + 2 * pad;
        int left = (int) x + 12;
        int top = (int) y - 12;
        int line = metrics.getHeight();
        g.setColor(new Color(0xF0100010, true));
        g.fillRect(left, top, width, 2 * line + 2 * pad);
        g.setColor(new Color(0xFF5000FF, true));
        g.drawRect(left, top, width, 2 * line + 2 * pad);
        g.setColor(Color.WHITE);
        g.drawString(title, left + pad, top + pad + metrics.getAscent());
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(detail, left + pad, top + line + metrics.getAscent());
    }
}
