package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenTabsTests {
    @Test
    void tabs_WideAndNarrow_StayInsideHeader() {
        for (ScreenTabs.Tab tab : ScreenTabs.Tab.values()) {
            BoardLayout.Rect rect = ScreenTabs.rect(640, tab);
            assertTrue(
                    rect.y() >= 0 && rect.bottom() <= ShellLayout.HEADER_HEIGHT,
                    "вкладка " + tab + " должна помещаться в шапку 26 px"
                            + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене tabs_WideAndNarrow_StayInsideHeader проверь это утверждение.");
        }
    }

    @Test
    void tabs_BothScales_AreRightAligned() {
        int right = ScreenTabs.rect(640, ScreenTabs.Tab.TREE).right();
        assertEquals(
                640 - BoardLayout.MARGIN,
                right,
                "вкладки обязаны прижиматься к правому краю шапки"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене tabs_BothScales_AreRightAligned проверь это утверждение.");
        assertEquals(
                960 - BoardLayout.MARGIN,
                ScreenTabs.rect(960, ScreenTabs.Tab.TREE).right(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене tabs_BothScales_AreRightAligned проверь это утверждение.");
    }

    @Test
    void tabs_BothScales_DoNotOverlap() {
        ScreenTabs.Tab previous = null;
        for (ScreenTabs.Tab tab : ScreenTabs.Tab.values()) {
            BoardLayout.Rect rect = ScreenTabs.rect(640, tab);
            if (previous != null) {
                assertTrue(
                        ScreenTabs.rect(640, previous).right() <= rect.x(),
                        "вкладки не должны перекрываться"
                                + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене tabs_BothScales_DoNotOverlap проверь это утверждение.");
            }
            previous = tab;
        }
    }
}
