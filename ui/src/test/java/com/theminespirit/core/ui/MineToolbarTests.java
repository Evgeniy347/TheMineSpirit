package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MineToolbarTests {
    @Test
    void layer_TopLayer_IsCountedFromTop() {
        MineToolbar toolbar = new MineToolbar(12, 75, 75);
        assertEquals(
                1,
                toolbar.layer(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене layer_TopLayer_IsCountedFromTop проверь это утверждение.");
        assertEquals(
                64,
                toolbar.layers(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене layer_TopLayer_IsCountedFromTop проверь это утверждение.");
        assertEquals(
                64,
                new MineToolbar(12, 75, 12).layer(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене layer_TopLayer_IsCountedFromTop проверь это утверждение.");
    }

    @Test
    void fraction_AtEdges_IsZeroAndOne() {
        MineToolbar toolbar = new MineToolbar(12, 75, 75);
        assertEquals(
                0.0,
                toolbar.fraction(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fraction_AtEdges_IsZeroAndOne проверь это утверждение.");
        assertEquals(
                1.0,
                new MineToolbar(12, 75, 12).fraction(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене fraction_AtEdges_IsZeroAndOne проверь это утверждение.");
    }

    @Test
    void yAt_AnyFraction_IsInverseOfFraction() {
        MineToolbar toolbar = new MineToolbar(12, 75, 40);
        assertEquals(
                toolbar.y(),
                toolbar.yAt(toolbar.fraction()),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене yAt_AnyFraction_IsInverseOfFraction проверь это утверждение.");
        assertEquals(
                75,
                toolbar.yAt(-1),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене yAt_AnyFraction_IsInverseOfFraction проверь это утверждение.");
        assertEquals(
                12,
                toolbar.yAt(2),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене yAt_AnyFraction_IsInverseOfFraction проверь это утверждение.");
    }

    @Test
    void step_BeyondEdges_ClampsAtEdges() {
        MineToolbar toolbar = new MineToolbar(12, 75, 75);
        assertEquals(
                75,
                toolbar.step(1).y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене step_BeyondEdges_ClampsAtEdges проверь это утверждение.");
        assertEquals(
                74,
                toolbar.step(-1).y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене step_BeyondEdges_ClampsAtEdges проверь это утверждение.");
        assertEquals(
                12,
                new MineToolbar(12, 75, 12).step(-5).y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене step_BeyondEdges_ClampsAtEdges проверь это утверждение.");
    }

    @Test
    void of_WrongLayer_ClampsToRange() {
        assertEquals(
                75,
                MineToolbar.of(12, 75, 999).y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_WrongLayer_ClampsToRange проверь это утверждение.");
        assertEquals(
                12,
                MineToolbar.of(12, 75, -3).y(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_WrongLayer_ClampsToRange проверь это утверждение.");
    }

    @Test
    void of_InvertedRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MineToolbar(75, 12, 40),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене of_InvertedRange_IsRejected проверь это утверждение.");
    }
}
