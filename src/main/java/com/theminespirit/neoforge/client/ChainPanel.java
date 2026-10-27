package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ChainPanel {
    private static final String KEY = ModIdentity.MOD_ID + ".craft.";
    private static final int LINE = 10;
    private static final int MAX_ITEM_LINES = 4;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int GOOD_COLOR = 0xFF2E7D32;
    private static final int BAD_COLOR = 0xFFB71C1C;

    private int x;
    private int y;
    private int width;
    private int height;
    private Optional<CraftTerminalChannel.ChainReply> reply = Optional.empty();

    void place(int left, int top, int panelWidth, int panelHeight) {
        x = left;
        y = top;
        width = panelWidth;
        height = panelHeight;
    }

    void show(CraftTerminalChannel.ChainReply next) {
        reply = Optional.of(next);
    }

    Optional<CraftTerminalChannel.ChainReply> reply() {
        return reply;
    }

    void render(GuiGraphics graphics, Font font) {
        if (reply.isEmpty()) {
            text(graphics, font, Component.translatable(KEY + "hint"), y, TEXT_COLOR);
            return;
        }
        CraftTerminalChannel.ChainReply shown = reply.get();
        int line = y;
        if (!shown.target().isEmpty()) {
            line = text(
                    graphics,
                    font,
                    Component.translatable(
                            KEY + "target",
                            shown.target().getHoverName(),
                            shown.target().getCount()),
                    line,
                    TEXT_COLOR);
            line = text(graphics, font, Component.translatable(KEY + "steps", shown.steps()), line, TEXT_COLOR);
        }
        line = items(graphics, font, KEY + "consumed", shown.consumed(), line, TEXT_COLOR);
        line = items(graphics, font, KEY + "leftovers", shown.leftovers(), line, TEXT_COLOR);
        line = items(graphics, font, KEY + "missing", shown.missing(), line, BAD_COLOR);
        if (!shown.notice().getString().isEmpty()) {
            text(graphics, font, shown.notice(), line, shown.feasible() ? GOOD_COLOR : BAD_COLOR);
        }
    }

    private int items(
            GuiGraphics graphics, Font font, String key, List<CraftTerminalChannel.Line> lines, int top, int color) {
        if (lines.isEmpty()) {
            return top;
        }
        int line = text(graphics, font, Component.translatable(key), top, color);
        for (int index = 0; index < lines.size() && index < MAX_ITEM_LINES; index++) {
            CraftTerminalChannel.Line item = lines.get(index);
            line = text(
                    graphics,
                    font,
                    Component.translatable(
                            KEY + "line", item.count(), item.sample().getHoverName()),
                    line,
                    color);
        }
        if (lines.size() > MAX_ITEM_LINES) {
            line = text(
                    graphics, font, Component.translatable(KEY + "more", lines.size() - MAX_ITEM_LINES), line, color);
        }
        return line;
    }

    private int text(GuiGraphics graphics, Font font, Component component, int top, int color) {
        int line = top;
        for (FormattedCharSequence part : font.split(component, width)) {
            if (line + LINE > y + height) {
                return line;
            }
            graphics.drawString(font, part, x, line, color, false);
            line += LINE;
        }
        return line;
    }
}
