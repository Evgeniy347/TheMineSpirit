package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import com.theminespirit.neoforge.terminal.CraftTerminalMenu;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

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
        Minecraft minecraft = Minecraft.getInstance();
        if (tab == ScreenTabs.Tab.CRAFT) {
            PacketDistributor.sendToServer(CraftTerminalChannel.Action.of(CraftTerminalChannel.Kind.OPEN));
            return;
        }
        if (minecraft.player != null && minecraft.player.containerMenu instanceof CraftTerminalMenu) {
            minecraft.player.closeContainer();
        }
        minecraft.setScreen(screenOf(tab));
    }

    private static Screen screenOf(ScreenTabs.Tab tab) {
        return switch (tab) {
            case BOARD -> new BoardScreen();
            case PLAN -> new TownMapScreen();
            case MINE -> new MineMapScreen();
            case CRAFT ->
                throw new IllegalStateException(
                        "Вкладку «Крафт» открывает сервер (меню с сеткой), а не клиентский экран");
        };
    }
}
