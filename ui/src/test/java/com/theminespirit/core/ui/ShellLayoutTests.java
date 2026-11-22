package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShellLayoutTests {
    private static final int WIDE_W = 960;
    private static final int WIDE_H = 540;
    private static final int NARROW_W = 640;
    private static final int NARROW_H = 360;

    @Test
    void of_WideGui_KeepsPartsApart() {
        ShellLayout shell = ShellLayout.of(WIDE_W, WIDE_H);
        assertEquals(
                new BoardLayout.Rect(0, 0, WIDE_W, ShellLayout.HEADER_HEIGHT),
                shell.header(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_WideGui_KeepsPartsApart проверь это утверждение.");
        assertEquals(
                ShellLayout.PAD,
                shell.content().x(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_WideGui_KeepsPartsApart проверь это утверждение.");
        assertEquals(
                ShellLayout.CONTENT_TOP,
                shell.content().y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_WideGui_KeepsPartsApart проверь это утверждение.");
        assertEquals(
                WIDE_W - 2 * ShellLayout.PAD,
                shell.content().width(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_WideGui_KeepsPartsApart проверь это утверждение.");
        assertEquals(
                WIDE_H - ShellLayout.FOOTER_HEIGHT,
                shell.footer().y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_WideGui_KeepsPartsApart проверь это утверждение.");
        assertTrue(
                shell.content().bottom() + ShellLayout.BOTTOM_GAP
                        <= shell.footer().y(),
                "контент не должен заходить в подвал: иначе подсказки клавиш закроют экран"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене of_WideGui_KeepsPartsApart проверь это утверждение.");
    }

    @Test
    void of_NarrowGui_FitsBodyBetweenToolbarAndFooter() {
        ShellLayout shell = ShellLayout.of(NARROW_W, NARROW_H);
        BoardLayout.Rect body = shell.body();
        assertEquals(
                ShellLayout.CONTENT_TOP + ShellLayout.TOOLBAR_HEIGHT + ShellLayout.TOOLBAR_GAP,
                body.y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_NarrowGui_FitsBodyBetweenToolbarAndFooter проверь это утверждение.");
        assertTrue(
                body.bottom() <= shell.content().bottom(),
                "тело должно помещаться в контент: на 640×360 каждый пиксель важен"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене of_NarrowGui_FitsBodyBetweenToolbarAndFooter проверь это утверждение.");
        assertTrue(
                shell.body().height() > 0,
                "на узком GUI тело не должно исчезать"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене of_NarrowGui_FitsBodyBetweenToolbarAndFooter проверь это утверждение.");
    }

    @Test
    void of_TinyScreen_ProducesNoNegativeSizes() {
        ShellLayout shell = ShellLayout.of(10, 10);
        assertTrue(
                shell.content().width() >= 0,
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_TinyScreen_ProducesNoNegativeSizes проверь это утверждение.");
        assertTrue(
                shell.content().height() >= 0,
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_TinyScreen_ProducesNoNegativeSizes проверь это утверждение.");
    }
}
