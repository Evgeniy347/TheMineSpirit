package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.BoardLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class BoardPanel {
    private static final ResourceLocation WINDOW =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
    private static final int BACKGROUND_TILE = 16;
    private static final int WINDOW_CONTENT_WIDTH = 252;
    private static final int WINDOW_CONTENT_HEIGHT = 140;
    private static final int WINDOW_TEXTURE = 256;
    private static final int MUTED_COLOR = 0xFFA0A0A0;

    private BoardPanel() {}

    static void drawVersion(
            GuiGraphics graphics, Font font, BoardLayout.Rect inner, BoardLayout.Rect window, String text) {
        float scale = 0.5F;
        float x = inner.x() + inner.width() - font.width(text) * scale - 2;
        graphics.pose().pushPose();
        graphics.pose().translate(x, window.y() + 7, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, MUTED_COLOR, false);
        graphics.pose().popPose();
    }

    static void drawBackground(GuiGraphics graphics, BoardLayout.Rect inner) {
        for (int tx = 0; tx < inner.width(); tx += BACKGROUND_TILE) {
            for (int ty = 0; ty < inner.height(); ty += BACKGROUND_TILE) {
                int w = Math.min(BACKGROUND_TILE, inner.width() - tx);
                int h = Math.min(BACKGROUND_TILE, inner.height() - ty);
                graphics.blit(BACKGROUND, inner.x() + tx, inner.y() + ty, 0, 0, w, h, BACKGROUND_TILE, BACKGROUND_TILE);
            }
        }
    }

    static void blitPanel(GuiGraphics graphics, BoardLayout.Rect window) {
        int x = window.x();
        int y = window.y();
        int width = window.width();
        int height = window.height();
        int left = BoardLayout.BORDER_LEFT;
        int top = BoardLayout.BORDER_TOP;
        int right = BoardLayout.BORDER_RIGHT;
        int bottom = BoardLayout.BORDER_BOTTOM;
        int innerWidth = width - left - right;
        int innerHeight = height - top - bottom;
        int sourceInnerWidth = WINDOW_CONTENT_WIDTH - left - right;
        int sourceInnerHeight = WINDOW_CONTENT_HEIGHT - top - bottom;
        if (width >= left + right && height >= top + bottom) {
            blit(graphics, x, y, 0, 0, left, top);
            blit(graphics, x + width - right, y, WINDOW_CONTENT_WIDTH - right, 0, right, top);
            blit(graphics, x, y + height - bottom, 0, WINDOW_CONTENT_HEIGHT - bottom, left, bottom);
            blit(
                    graphics,
                    x + width - right,
                    y + height - bottom,
                    WINDOW_CONTENT_WIDTH - right,
                    WINDOW_CONTENT_HEIGHT - bottom,
                    right,
                    bottom);
        }
        if (innerWidth > 0 && height >= top + bottom) {
            blitTiledX(graphics, x + left, y, left, 0, innerWidth, top);
            blitTiledX(
                    graphics, x + left, y + height - bottom, left, WINDOW_CONTENT_HEIGHT - bottom, innerWidth, bottom);
        }
        if (innerHeight > 0 && width >= left + right) {
            blitTiledY(graphics, x, y + top, 0, top, left, innerHeight);
            blitTiledY(graphics, x + width - right, y + top, WINDOW_CONTENT_WIDTH - right, top, right, innerHeight);
        }
        if (innerWidth > 0 && innerHeight > 0) {
            for (int dx = 0; dx < innerWidth; dx += sourceInnerWidth) {
                int partWidth = Math.min(sourceInnerWidth, innerWidth - dx);
                for (int dy = 0; dy < innerHeight; dy += sourceInnerHeight) {
                    int partHeight = Math.min(sourceInnerHeight, innerHeight - dy);
                    blit(graphics, x + left + dx, y + top + dy, left, top, partWidth, partHeight);
                }
            }
        }
    }

    private static void blitTiledX(GuiGraphics graphics, int destX, int destY, int u, int v, int width, int height) {
        int step = WINDOW_CONTENT_WIDTH - BoardLayout.BORDER_LEFT - BoardLayout.BORDER_RIGHT;
        for (int dx = 0; dx < width; dx += step) {
            blit(graphics, destX + dx, destY, u, v, Math.min(step, width - dx), height);
        }
    }

    private static void blitTiledY(GuiGraphics graphics, int destX, int destY, int u, int v, int width, int height) {
        int step = WINDOW_CONTENT_HEIGHT - BoardLayout.BORDER_TOP - BoardLayout.BORDER_BOTTOM;
        for (int dy = 0; dy < height; dy += step) {
            blit(graphics, destX, destY + dy, u, v, width, Math.min(step, height - dy));
        }
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(WINDOW, x, y, u, v, width, height, WINDOW_TEXTURE, WINDOW_TEXTURE);
    }
}
