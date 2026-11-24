package com.theminespirit.neoforge.board;

import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.WallLayout;
import com.theminespirit.core.town.WallPiece;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.server.level.ServerLevel;

final class TownPreview {
    static final int SCALE = 2;
    private static final Color STREET = new Color(120, 120, 120, 150);
    private static final Color WALL = new Color(40, 40, 40, 220);
    private static final Color BORDER = new Color(20, 20, 20, 230);
    private static final List<Color> STAGE_FILL = List.of(
            new Color(255, 214, 102, 150),
            new Color(255, 176, 84, 150),
            new Color(171, 216, 255, 150),
            new Color(196, 255, 171, 150));

    private TownPreview() {}

    static byte[] png(ServerLevel level, TownSurvey.Frame frame, TownPlan plan, int scale) {
        ColumnMask region = frame.region();
        int x1 = region.minX();
        int z1 = region.minZ();
        int x2 = region.minX() + region.width() - 1;
        int z2 = region.minZ() + region.depth() - 1;
        TerrainPainter.check(x1, z1, x2, z2, scale);
        BufferedImage image = TerrainPainter.draw(level, x1, z1, x2, z2, scale);
        Graphics2D graphics = TerrainPainter.graphics(image);
        paint(graphics, plan, x1, z1, scale);
        TownPreview.grid(graphics, x1, z1, x2, z2, scale);
        graphics.dispose();
        return encode(image);
    }

    static byte[] terrain(ServerLevel level, int x1, int z1, int x2, int z2, int scale) {
        BufferedImage image = TerrainPainter.draw(level, x1, z1, x2, z2, scale);
        Graphics2D graphics = TerrainPainter.graphics(image);
        TownPreview.grid(graphics, x1, z1, x2, z2, scale);
        graphics.dispose();
        return encode(image);
    }

    private static void grid(Graphics2D graphics, int x1, int z1, int x2, int z2, int scale) {
        TerrainPainter.grid(graphics, x1, z1, x2, z2, scale);
        graphics.setFont(graphics.getFont().deriveFont((float) Math.max(10, scale * 4)));
        TerrainPainter.label(graphics, "1 клетка = 1 блок", 4, (z2 - z1 + 1) * scale - 6);
    }

    private static void paint(Graphics2D graphics, TownPlan plan, int x1, int z1, int scale) {
        streets(graphics, plan.streets().main(), x1, z1, scale);
        streets(graphics, plan.streets().side(), x1, z1, scale);
        streets(graphics, plan.streets().ring(), x1, z1, scale);
        for (Placement plot : plan.placements()) {
            int px = (plot.x() - x1) * scale;
            int pz = (plot.z() - z1) * scale;
            int pw = plot.width() * scale;
            int pd = plot.depth() * scale;
            graphics.setColor(STAGE_FILL.get(Math.floorMod(plot.stage() - 1, STAGE_FILL.size())));
            graphics.fillRect(px, pz, pw, pd);
            graphics.setColor(BORDER);
            graphics.drawRect(px, pz, pw - 1, pd - 1);
            graphics.setFont(graphics.getFont().deriveFont(Math.max(9f, scale * 3.5f)));
            TerrainPainter.label(graphics, plot.id(), px + 3, pz + 12);
            door(graphics, plot, px, pz, pw, pd);
        }
        wall(graphics, plan, x1, z1, scale);
    }

    private static void streets(Graphics2D graphics, ColumnMask mask, int x1, int z1, int scale) {
        graphics.setColor(STREET);
        for (ColumnMask.Cell cell : mask.cells()) {
            graphics.fillRect((cell.x() - x1) * scale, (cell.z() - z1) * scale, scale, scale);
        }
    }

    private static void wall(Graphics2D graphics, TownPlan plan, int x1, int z1, int scale) {
        if (plan.fence().posts().isEmpty()) {
            return;
        }
        PresetLibrary presets = DataTables.INSTANCE.presets().current();
        List<WallPiece> pieces;
        try {
            pieces = WallLayout.of(presets, StageSchemes.wall())
                    .openRuns(plan.fence().posts(), plan.fence().gates());
        } catch (IllegalStateException unfolded) {
            pieces = List.of();
        }
        if (pieces.isEmpty()) {
            posts(graphics, plan, x1, z1, scale);
            return;
        }
        Map<String, Integer> schemes = new LinkedHashMap<>();
        for (WallPiece piece : pieces) {
            Preset preset = presets.byId(piece.scheme())
                    .orElseThrow(() -> new IllegalStateException(
                            "presets.json: нет схемы фрагмента стены '" + piece.scheme() + "' для карты"));
            int width = piece.rotation().rotatedSizeX(preset.sizeX(), preset.sizeZ());
            int depth = piece.rotation().rotatedSizeZ(preset.sizeX(), preset.sizeZ());
            int px = (piece.x() - x1) * scale;
            int pz = (piece.z() - z1) * scale;
            graphics.setColor(WALL);
            graphics.fillRect(px, pz, width * scale, depth * scale);
            graphics.setColor(BORDER);
            graphics.drawRect(px, pz, width * scale - 1, depth * scale - 1);
            int index = schemes.computeIfAbsent(piece.scheme(), ignored -> schemes.size() + 1);
            graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, Math.max(7f, scale * 3f)));
            TerrainPainter.label(graphics, String.valueOf(index), px + 2, pz + Math.max(8, scale * 4));
        }
        legend(graphics, schemes, scale);
    }

    static void legend(Graphics2D graphics, Map<String, Integer> schemes, int scale) {
        if (schemes.isEmpty()) {
            return;
        }
        float size = Math.max(9f, scale * 4.5f);
        graphics.setFont(graphics.getFont().deriveFont(size));
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> lines = new ArrayList<>(schemes.size());
        int width = 0;
        for (Map.Entry<String, Integer> scheme : schemes.entrySet()) {
            String line = scheme.getValue() + " — " + scheme.getKey();
            lines.add(line);
            width = Math.max(width, metrics.stringWidth(line));
        }
        int pad = Math.max(4, scale * 2);
        int height = lines.size() * metrics.getHeight() + pad * 2;
        graphics.setColor(new Color(0, 0, 0, 190));
        graphics.fillRect(4, 20, width + pad * 2, height);
        int y = 20 + pad + metrics.getAscent();
        for (String line : lines) {
            TerrainPainter.label(graphics, line, 4 + pad, y);
            y += metrics.getHeight();
        }
    }

    private static void posts(Graphics2D graphics, TownPlan plan, int x1, int z1, int scale) {
        graphics.setColor(WALL);
        for (ColumnMask.Cell cell : plan.fence().posts()) {
            graphics.fillRect((cell.x() - x1) * scale, (cell.z() - z1) * scale, scale, scale);
        }
    }

    private static void door(Graphics2D graphics, Placement plot, int px, int pz, int pw, int pd) {
        int size = Math.max(4, pw / 30);
        int midX = px + pw / 2;
        int midZ = pz + pd / 2;
        graphics.setColor(BORDER);
        switch (plot.facing()) {
            case NORTH -> {
                graphics.fillRect(midX - size / 2, pz - size, size, size);
                TerrainPainter.label(graphics, "N", midX + 3, pz + 12);
            }
            case SOUTH -> {
                graphics.fillRect(midX - size / 2, pz + pd, size, size);
                TerrainPainter.label(graphics, "S", midX + 3, pz + pd + 12);
            }
            case WEST -> {
                graphics.fillRect(px - size, midZ - size / 2, size, size);
                TerrainPainter.label(graphics, "W", px + 3, midZ + 14);
            }
            case EAST -> {
                graphics.fillRect(px + pw, midZ - size / 2, size, size);
                TerrainPainter.label(graphics, "E", px + pw - 12, midZ + 14);
            }
        }
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Карта поселения не записана в PNG: " + error.getMessage(), error);
        }
    }
}
