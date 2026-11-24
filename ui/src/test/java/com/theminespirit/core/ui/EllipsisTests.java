package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class EllipsisTests {
    private static final ToIntFunction<String> SIX = text -> text.codePointCount(0, text.length()) * 6;

    @Test
    void fit_TextFitsWidth_StaysUnchanged() {
        Ellipsis.Fitted fitted = Ellipsis.fit("Доски", 60, SIX);
        assertEquals(
                "Доски",
                fitted.text(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_TextFitsWidth_StaysUnchanged проверь это утверждение.");
        assertFalse(
                fitted.trimmed(),
                "влезающий текст нельзя помечать обрезанным: тултип не нужен"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене fit_TextFitsWidth_StaysUnchanged проверь это утверждение.");
    }

    @Test
    void fit_LongText_EndsWithMarkAndFitsWidth() {
        String text = "Сделать каменную кирку из булыжника";
        Ellipsis.Fitted fitted = Ellipsis.fit(text, 120, SIX);
        assertTrue(
                fitted.trimmed(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_LongText_EndsWithMarkAndFitsWidth проверь это утверждение.");
        assertTrue(
                fitted.text().endsWith(Ellipsis.MARK),
                "обрезанный текст обязан кончаться многоточием"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене fit_LongText_EndsWithMarkAndFitsWidth проверь это утверждение.");
        assertTrue(
                SIX.applyAsInt(fitted.text()) <= 120,
                "обрезанный текст должен влезать в ширину"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене fit_LongText_EndsWithMarkAndFitsWidth проверь это утверждение.");
        assertTrue(
                fitted.text().length() < text.length(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_LongText_EndsWithMarkAndFitsWidth проверь это утверждение.");
    }

    @Test
    void fit_WidthBelowMark_ReturnsEmptyTrimmed() {
        Ellipsis.Fitted fitted = Ellipsis.fit("Доски", 3, SIX);
        assertEquals(
                "",
                fitted.text(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_WidthBelowMark_ReturnsEmptyTrimmed проверь это утверждение.");
        assertTrue(
                fitted.trimmed(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_WidthBelowMark_ReturnsEmptyTrimmed проверь это утверждение.");
    }

    @Test
    void fit_ZeroWidth_DrawsNothing() {
        assertEquals(
                "",
                Ellipsis.fit("Доски", 0, SIX).text(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_ZeroWidth_DrawsNothing проверь это утверждение.");
        assertEquals(
                "",
                Ellipsis.fit("Доски", -10, SIX).text(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_ZeroWidth_DrawsNothing проверь это утверждение.");
    }

    @Test
    void fit_MultibyteText_KeepsCodePoints() {
        String text = "Доски 😀 бочки 😀 факелы";
        Ellipsis.Fitted fitted = Ellipsis.fit(text, 90, SIX);
        String body = fitted.text().substring(0, fitted.text().length() - 1);
        assertFalse(
                body.isEmpty(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_MultibyteText_KeepsCodePoints проверь это утверждение.");
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                assertTrue(
                        i + 1 < body.length() && Character.isLowSurrogate(body.charAt(i + 1)),
                        "обрезка не должна оставлять половинку эмодзи"
                                + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене fit_MultibyteText_KeepsCodePoints проверь это утверждение.");
            }
            if (Character.isLowSurrogate(ch)) {
                assertTrue(
                        i > 0 && Character.isHighSurrogate(body.charAt(i - 1)),
                        "обрезка не должна начинаться с младшего суррогата"
                                + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене fit_MultibyteText_KeepsCodePoints проверь это утверждение.");
            }
        }
    }

    @Test
    void fit_NullText_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> Ellipsis.fit(null, 10, SIX),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_NullText_IsRejected проверь это утверждение.");
    }
}
