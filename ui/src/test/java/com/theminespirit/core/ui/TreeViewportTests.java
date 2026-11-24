package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeViewportTests {
    @Test
    void fit_StageOneWideArea_FitsInside() {
        TreeViewport viewport = TreeViewport.fit(910, 441, 508, 580);
        assertTrue(
                viewport.scale() < 1,
                "этап 1 выше области и обязан вписываться уменьшением"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене fit_StageOneWideArea_FitsInside проверь это утверждение.");
        assertTrue(
                viewport.scaledHeight() <= 441,
                "вписанное древо обязано помещаться по высоте"
                        + " Иначе поломка пройдёт незамеченной и сломает экран поселения при следующей правке. При смене fit_StageOneWideArea_FitsInside проверь это утверждение.");
        assertTrue(
                viewport.scaledWidth() <= 910,
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fit_StageOneWideArea_FitsInside проверь это утверждение.");
    }

    @Test
    void zoomIn_FromFit_StepsByQuarterAndClamps() {
        TreeViewport viewport = TreeViewport.fit(900, 600, 400, 400);
        double base = viewport.scale();
        assertEquals(
                base * 1.25,
                viewport.zoomIn().scale(),
                1e-9,
                "Шаг зума обязан быть ровно 1.25×: иначе связь между кнопками и процентом разъедется, а рамки вылезут за область. При смене zoomIn_FromFit_StepsByQuarterAndClamps проверь это утверждение.");
        TreeViewport max = viewport;
        for (int i = 0; i < TreeViewport.MAX_STEPS + 2; i++) {
            max = max.zoomIn();
        }
        assertEquals(
                TreeViewport.MAX_STEPS,
                max.steps(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене zoomIn_FromFit_StepsByQuarterAndClamps проверь это утверждение.");
        assertEquals(
                0,
                TreeViewport.fit(900, 600, 400, 400).zoomOut().steps(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене zoomIn_FromFit_StepsByQuarterAndClamps проверь это утверждение.");
    }

    @Test
    void percent_AnyScale_ShowsScaleInPercent() {
        assertEquals(
                100,
                TreeViewport.fit(900, 600, 400, 400).percent(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене percent_AnyScale_ShowsScaleInPercent проверь это утверждение.");
        assertEquals(
                125,
                TreeViewport.fit(900, 600, 400, 400).zoomIn().percent(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене percent_AnyScale_ShowsScaleInPercent проверь это утверждение.");
    }

    @Test
    void clampOffset_ContentLargerThanArea_KeepsInsideArea() {
        assertEquals(
                0,
                TreeViewport.clampOffset(50, 400, 300),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене clampOffset_ContentLargerThanArea_KeepsInsideArea проверь это утверждение.");
        assertEquals(
                -100,
                TreeViewport.clampOffset(-500, 400, 500),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене clampOffset_ContentLargerThanArea_KeepsInsideArea проверь это утверждение.");
        assertEquals(
                -40,
                TreeViewport.clampOffset(-40, 400, 500),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене clampOffset_ContentLargerThanArea_KeepsInsideArea проверь это утверждение.");
    }
}
