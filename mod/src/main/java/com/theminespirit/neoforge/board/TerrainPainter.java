package com.theminespirit.neoforge.board;

import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

final class TerrainPainter {
    static final int MAX_CELLS = 160_000;
    static final int MAX_PIXELS = 4096;
    private static final int FALLBACK = 0x8A8A8A;
    private static final Color GRID = new Color(255, 255, 255, 70);
    private static final Color LABEL = new Color(255, 255, 255, 235);
    private static final Color SHADOW = new Color(0, 0, 0, 200);

    private TerrainPainter() {}

    static BufferedImage draw(ServerLevel level, int x1, int z1, int x2, int z2, int scale) {
        check(x1, z1, x2, z2, scale);
        int width = x2 - x1 + 1;
        int depth = z2 - z1 + 1;
        BufferedImage image = new BufferedImage(width * scale, depth * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                graphics.setColor(new Color(cell(level, x, z)));
                graphics.fillRect((x - x1) * scale, (z - z1) * scale, scale, scale);
            }
        }
        graphics.dispose();
        return image;
    }

    static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return graphics;
    }

    static void grid(Graphics2D graphics, int x1, int z1, int x2, int z2, int scale) {
        graphics.setColor(GRID);
        for (int x = x1; x <= x2 + 1; x += 16) {
            int px = (x - x1) * scale;
            graphics.drawLine(px, 0, px, (z2 - z1 + 1) * scale);
        }
        for (int z = z1; z <= z2 + 1; z += 16) {
            int pz = (z - z1) * scale;
            graphics.drawLine(0, pz, (x2 - x1 + 1) * scale, pz);
        }
        graphics.setFont(graphics.getFont().deriveFont((float) Math.max(10, scale * 4)));
        for (int x = x1 - Math.floorMod(x1, 32) + 32; x <= x2; x += 32) {
            label(graphics, String.valueOf(x), (x - x1) * scale + 2, 12);
        }
        for (int z = z1 - Math.floorMod(z1, 32) + 32; z <= z2; z += 32) {
            label(graphics, String.valueOf(z), 2, (z - z1) * scale + 12);
        }
    }

    static void label(Graphics2D graphics, String text, int x, int y) {
        graphics.setColor(SHADOW);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(LABEL);
        graphics.drawString(text, x, y);
    }

    static void check(int x1, int z1, int x2, int z2, int scale) {
        if (x1 >= x2 || z1 >= z2) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Рамка карты " + x1 + "," + z1 + " → " + x2 + "," + z2 + " пуста: x1<x2 и z1<z2",
                    Map.of());
        }
        long cells = (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        if (cells > MAX_CELLS) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Рамка карты " + cells + " клеток больше предела " + MAX_CELLS + ": уменьшите участок",
                    Map.of("max", MAX_CELLS));
        }
        if (scale < 1 || scale > 8) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST, "Масштаб " + scale + ": допустимо 1..8 пикселей на клетку", Map.of());
        }
        long pixels = (long) (x2 - x1 + 1) * (z2 - z1 + 1) * scale * scale;
        if (pixels > (long) MAX_PIXELS * MAX_PIXELS) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Картинка " + pixels + " пикселей больше " + MAX_PIXELS + "×" + MAX_PIXELS + ": уменьшите масштаб",
                    Map.of("max_pixels", MAX_PIXELS));
        }
    }

    private static int cell(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, Math.max(level.getMinBuildHeight(), top - 1), z);
        FluidState fluid = level.getFluidState(pos.above());
        if (!fluid.isEmpty()) {
            if (fluid.is(Fluids.WATER)) {
                return fluid.isSource() ? 0x3F76E4 : 0x5A8BE8;
            }
            if (fluid.is(Fluids.LAVA)) {
                return 0xE85D0C;
            }
        }
        BlockState state = level.getBlockState(pos);
        int color = state.getMapColor(level, pos).calculateRGBColor(MapColor.Brightness.NORMAL);
        return color == 0 ? FALLBACK : color;
    }
}
