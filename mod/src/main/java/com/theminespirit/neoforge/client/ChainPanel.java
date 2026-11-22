package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ChainPanel {
    private static final String KEY = ModIdentity.MOD_ID + ".craft.";
    private static final int LINE = 10;
    private static final int MAX_ITEM_LINES = 3;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int MUTED = 0xFF6A6A6A;
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
            text(graphics, font, Component.translatable(KEY + "hint"), y, MUTED);
            return;
        }
        CraftTerminalChannel.ChainReply shown = reply.get();
        graphics.enableScissor(x - 2, y - 2, x + width + 2, y + height);
        int line = y;
        if (!shown.target().isEmpty()) {
            graphics.renderItem(shown.target(), x, line);
            graphics.drawString(
                    font,
                    TextClip.text(
                            font,
                            Component.translatable(
                                    KEY + "target",
                                    shown.target().getHoverName(),
                                    shown.target().getCount()),
                            width - 20),
                    x + 20,
                    line + 1,
                    TEXT_COLOR,
                    false);
            graphics.drawString(
                    font,
                    Component.translatable(KEY + "steps", shown.steps()).getString(),
                    x + 20,
                    line + 11,
                    MUTED,
                    false);
            line += 23;
        }
        line = group(graphics, font, Component.translatable(KEY + "consumed"), shown.consumed(), line, TEXT_COLOR);
        line = group(graphics, font, Component.translatable(KEY + "leftovers"), shown.leftovers(), line, TEXT_COLOR);
        if (shown.missing().isEmpty()) {
            graphics.drawString(
                    font, Component.translatable(KEY + "missing.none").getString(), x, line, GOOD_COLOR, false);
            line += 12;
        } else {
            line = group(graphics, font, Component.translatable(KEY + "missing"), shown.missing(), line, BAD_COLOR);
        }
        if (!shown.notice().getString().isEmpty() && line + LINE <= y + height) {
            graphics.drawString(
                    font,
                    TextClip.text(font, shown.notice(), width),
                    x,
                    line,
                    shown.feasible() ? GOOD_COLOR : BAD_COLOR,
                    false);
        }
        graphics.disableScissor();
    }

    private int group(
            GuiGraphics graphics,
            Font font,
            Component title,
            List<CraftTerminalChannel.Line> lines,
            int top,
            int color) {
        if (lines.isEmpty()) {
            return top;
        }
        int line = top;
        graphics.drawString(font, title.getString(), x, line, color, false);
        line += 10;
        for (int index = 0; index < lines.size() && index < MAX_ITEM_LINES; index++) {
            if (line + LINE > y + height) {
                return line;
            }
            CraftTerminalChannel.Line item = lines.get(index);
            graphics.renderItem(item.sample(), x, line - 1);
            graphics.drawString(
                    font,
                    TextClip.text(
                            font,
                            Component.literal("×" + item.count() + " "
                                    + item.sample().getHoverName().getString()),
                            width - 14),
                    x + 14,
                    line,
                    color,
                    false);
            line += 10;
        }
        if (lines.size() > MAX_ITEM_LINES && line + LINE <= y + height) {
            graphics.drawString(
                    font,
                    Component.translatable(KEY + "more", lines.size() - MAX_ITEM_LINES)
                            .getString(),
                    x + 14,
                    line,
                    MUTED,
                    false);
            line += 10;
        }
        return line + 4;
    }

    private void text(GuiGraphics graphics, Font font, Component component, int top, int color) {
        int line = top;
        for (var part : font.split(component, width)) {
            if (line + LINE > y + height) {
                return;
            }
            graphics.drawString(font, part, x, line, color, false);
            line += LINE;
        }
    }
}
