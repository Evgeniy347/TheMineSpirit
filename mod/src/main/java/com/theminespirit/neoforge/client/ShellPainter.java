package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.RefreshClock;
import com.theminespirit.core.ui.ShellLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class ShellPainter {
    static final String KEY = ModIdentity.MOD_ID + ".shell.";
    static final int HEADER_BG = 0xF00A0C0F;
    static final int HEADER_LINE = 0xFF2A3038;
    static final int FOOTER_BG = 0xE60A0C0F;
    static final int GOLD = 0xFFE0B050;
    static final int TEXT = 0xFFE8ECF1;
    static final int MUTED = 0xFF98A0AB;
    static final int DIM = 0xFF6B7480;
    static final int GOOD = 0xFF6FA548;

    private ShellPainter() {}

    static void background(GuiGraphics graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, 0xFF181D16, 0xFF0A0D0A);
        graphics.fill(0, 0, width, height, 0x8C080A0A);
        graphics.fill(0, 0, width, ShellLayout.HEADER_HEIGHT, HEADER_BG);
        graphics.fill(0, ShellLayout.HEADER_HEIGHT, width, ShellLayout.HEADER_HEIGHT + 1, HEADER_LINE);
        int footerTop = height - ShellLayout.FOOTER_HEIGHT;
        if (footerTop > ShellLayout.HEADER_HEIGHT) {
            graphics.fill(0, footerTop, width, height, FOOTER_BG);
            graphics.fill(0, footerTop, width, footerTop + 1, 0xFF20262D);
        }
    }

    static void titles(GuiGraphics graphics, Font font, Component title, @Nullable Component subtitle) {
        graphics.fill(ShellLayout.PAD, 8, ShellLayout.PAD + 2, 18, GOLD);
        graphics.drawString(font, title, ShellLayout.titleX(), ShellLayout.titleY(), TEXT, false);
        if (subtitle != null) {
            graphics.drawString(
                    font,
                    subtitle,
                    ShellLayout.titleX() + font.width(title) + 10,
                    ShellLayout.titleY() + 1,
                    MUTED,
                    false);
        }
    }

    static void footer(
            GuiGraphics graphics, Font font, Component hints, RefreshClock clock, int nowTick, int width, int height) {
        int y = height - ShellLayout.FOOTER_HEIGHT + 4;
        graphics.drawString(font, hints, ShellLayout.PAD, y, DIM, false);
        int seconds = clock.seconds(nowTick);
        Component label = seconds < 0
                ? Component.translatable(KEY + "waiting")
                : Component.translatable(KEY + "updated", seconds);
        int labelWidth = font.width(label);
        int x = width - ShellLayout.PAD - labelWidth;
        if (x > ShellLayout.PAD && x > ShellLayout.PAD + font.width(hints) + 8) {
            int dot = seconds < 0 ? DIM : GOOD;
            graphics.fill(x - 7, y + 2, x - 3, y + 6, dot);
            graphics.drawString(font, label, x, y, DIM, false);
        }
    }
}
