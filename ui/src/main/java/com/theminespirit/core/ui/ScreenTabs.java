package com.theminespirit.core.ui;

import java.util.Locale;

public final class ScreenTabs {
    public static final int WIDTH = 54;
    public static final int HEIGHT = 16;
    public static final int GAP = 2;
    public static final int TOP = 5;

    public enum Tab {
        BOARD(0),
        PLAN(1),
        MINE(2),
        CRAFT(3),
        TREE(4);

        private final int slot;

        Tab(int slot) {
            this.slot = slot;
        }
    }

    private ScreenTabs() {}

    public static String key(Tab tab) {
        return "theminespirit.tab." + tab.name().toLowerCase(Locale.ROOT);
    }

    public static int tabsWidth() {
        int count = Tab.values().length;
        return count * WIDTH + (count - 1) * GAP;
    }

    public static int tabStart(int screenWidth) {
        return Math.max(ShellLayout.PAD, screenWidth - BoardLayout.MARGIN - tabsWidth());
    }

    public static BoardLayout.Rect rect(int screenWidth, Tab tab) {
        int start = tabStart(screenWidth);
        return new BoardLayout.Rect(start + tab.slot * (WIDTH + GAP), TOP, WIDTH, HEIGHT);
    }
}
