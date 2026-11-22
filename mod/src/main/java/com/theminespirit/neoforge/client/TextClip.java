package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.Ellipsis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class TextClip {
    private TextClip() {}

    record Line(String text, boolean trimmed) {}

    static Line fit(Font font, Component text, int width) {
        Ellipsis.Fitted fitted = Ellipsis.fit(text.getString(), width, font::width);
        return new Line(fitted.text(), fitted.trimmed());
    }

    static String text(Font font, Component text, int width) {
        return fit(font, text, width).text();
    }

    static void draw(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, text(font, text, width), x, y, color, false);
    }
}
