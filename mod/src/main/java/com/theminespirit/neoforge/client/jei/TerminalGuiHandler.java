package com.theminespirit.neoforge.client.jei;

import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.CraftLayout;
import com.theminespirit.neoforge.client.CraftTerminalScreen;
import java.util.List;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.renderer.Rect2i;

final class TerminalGuiHandler implements IGuiContainerHandler<CraftTerminalScreen> {
    @Override
    public List<Rect2i> getGuiExtraAreas(CraftTerminalScreen screen) {
        BoardLayout.Rect column = CraftLayout.guiArea(screen.getGuiLeft(), screen.getGuiTop());
        return List.of(new Rect2i(column.x(), column.y(), column.width(), column.height()));
    }
}
