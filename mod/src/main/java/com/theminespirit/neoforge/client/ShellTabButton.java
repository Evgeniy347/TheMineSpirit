package com.theminespirit.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class ShellTabButton extends Button {
    private static final int ACTIVE_BORDER = 0xFF4A5C34;
    private static final int ACTIVE_FILL = 0xFF26301D;
    private static final int HOVER_BORDER = 0xFF3A434E;
    private static final int HOVER_FILL = 0xFF1D232B;
    private static final int IDLE_BORDER = 0xFF2B323B;
    private static final int IDLE_FILL = 0xFF14181E;
    private static final int ACTIVE_TEXT = 0xFFE6D9A8;
    private static final int IDLE_TEXT = 0xFF98A0AB;

    private boolean current;

    ShellTabButton(Button.Builder builder, boolean current) {
        super(builder);
        this.current = current;
    }

    void current(boolean next) {
        current = next;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = active && isHovered();
        int border = current ? ACTIVE_BORDER : hovered ? HOVER_BORDER : IDLE_BORDER;
        int fill = current ? ACTIVE_FILL : hovered ? HOVER_FILL : IDLE_FILL;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, fill);
        Component message = getMessage();
        int textX = getX() + (getWidth() - Minecraft.getInstance().font.width(message)) / 2;
        graphics.drawString(
                Minecraft.getInstance().font, message, textX, getY() + 4, current ? ACTIVE_TEXT : IDLE_TEXT, false);
        if (current) {
            graphics.fill(
                    getX() + 6,
                    getY() + getHeight() - 2,
                    getX() + getWidth() - 6,
                    getY() + getHeight(),
                    ShellPainter.GOLD);
        }
    }
}
