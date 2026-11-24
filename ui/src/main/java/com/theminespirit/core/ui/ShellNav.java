package com.theminespirit.core.ui;

import org.jspecify.annotations.Nullable;

public final class ShellNav {
    public static final int KEY_J = 74;
    public static final int KEY_K = 75;

    private ShellNav() {}

    public static ScreenTabs.@Nullable Tab target(int keyCode, boolean textFocused, ScreenTabs.Tab current) {
        if (textFocused) {
            return null;
        }
        ScreenTabs.Tab tab = switch (keyCode) {
            case KEY_J -> ScreenTabs.Tab.TREE;
            case KEY_K -> ScreenTabs.Tab.BOARD;
            default -> null;
        };
        return tab == current ? null : tab;
    }
}
