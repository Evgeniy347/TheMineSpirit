package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.ScreenTabs;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class ScreenTabButtons {
    private ScreenTabButtons() {}

    static void add(Consumer<Button> widgets, int screenWidth, ScreenTabs.Tab current) {
        for (ScreenTabs.Tab tab : ScreenTabs.Tab.values()) {
            BoardLayout.Rect rect = ScreenTabs.rect(screenWidth, tab);
            Button button = Button.builder(Component.translatable(ScreenTabs.key(tab)), pressed -> open(tab))
                    .bounds(rect.x(), rect.y(), rect.width(), rect.height())
                    .build();
            button.active = tab != current;
            widgets.accept(button);
        }
    }

    private static void open(ScreenTabs.Tab tab) {
        Minecraft.getInstance()
                .setScreen(
                        switch (tab) {
                            case BOARD -> new BoardScreen();
                            case PLAN -> new TownMapScreen();
                            case MINE -> new MineMapScreen();
                        });
    }
}
