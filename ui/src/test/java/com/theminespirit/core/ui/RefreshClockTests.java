package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RefreshClockTests {
    @Test
    void clock_NeverSeen_HasNoSeconds() {
        assertEquals(
                -1,
                RefreshClock.NEVER.seconds(500),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене clock_NeverSeen_HasNoSeconds проверь это утверждение.");
    }

    @Test
    void seconds_TicksSinceResponse_CountAsTwentieths() {
        assertEquals(
                0,
                RefreshClock.at(100).seconds(110),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене seconds_TicksSinceResponse_CountAsTwentieths проверь это утверждение.");
        assertEquals(
                1,
                RefreshClock.at(100).seconds(120),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене seconds_TicksSinceResponse_CountAsTwentieths проверь это утверждение.");
        assertEquals(
                3,
                RefreshClock.at(100).seconds(161),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене seconds_TicksSinceResponse_CountAsTwentieths проверь это утверждение.");
    }

    @Test
    void seconds_ClockGoingBackwards_ShowsZero() {
        assertEquals(
                0,
                RefreshClock.at(100).seconds(40),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене seconds_ClockGoingBackwards_ShowsZero проверь это утверждение.");
    }

    @Test
    void at_NegativeTick_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RefreshClock.at(-1),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене at_NegativeTick_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshClock(-5, true),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене at_NegativeTick_IsRejected проверь это утверждение.");
    }
}
