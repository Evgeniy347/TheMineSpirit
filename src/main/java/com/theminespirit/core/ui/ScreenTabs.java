package com.theminespirit.core.ui;

import java.util.Locale;

public final class ScreenTabs {
    public static final int WIDTH = 64;
    public static final int HEIGHT = 16;
    public static final int GAP = 2;
    public static final int TOP = 2;

    public enum Tab {
        BOARD(0),
        PLAN(1);

        private final int slot;

        Tab(int slot) {
            this.slot = slot;
        }
    }

    private ScreenTabs() {}

    public static String key(Tab tab) {
        return "theminespirit.tab." + tab.name().toLowerCase(Locale.ROOT);
    }

    public static BoardLayout.Rect rect(int screenWidth, Tab tab) {
        int count = Tab.values().length;
        int start = screenWidth - BoardLayout.MARGIN - count * WIDTH - (count - 1) * GAP;
        return new BoardLayout.Rect(start + tab.slot * (WIDTH + GAP), TOP, WIDTH, HEIGHT);
    }
}
