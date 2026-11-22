package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoardSearchTests {
    @Test
    void query_Empty_MatchesEverything() {
        assertTrue(
                BoardSearch.matches("Сделать кирку", ""),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_Empty_MatchesEverything проверь это утверждение.");
        assertTrue(
                BoardSearch.matches("Сделать кирку", "   "),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_Empty_MatchesEverything проверь это утверждение.");
        assertTrue(
                BoardSearch.matches("Сделать кирку", null),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_Empty_MatchesEverything проверь это утверждение.");
    }

    @Test
    void query_DifferentCase_MatchesSubstring() {
        assertTrue(
                BoardSearch.matches("Сделать каменную кирку", "КИРКУ"),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_DifferentCase_MatchesSubstring проверь это утверждение.");
        assertTrue(
                BoardSearch.matches("Сделать каменную кирку", "камен"),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_DifferentCase_MatchesSubstring проверь это утверждение.");
    }

    @Test
    void query_MultipleWords_AllMustMatch() {
        assertTrue(
                BoardSearch.matches("Сделать каменную кирку spirit-1", "кирку spirit"),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_MultipleWords_AllMustMatch проверь это утверждение.");
        assertFalse(
                BoardSearch.matches("Сделать каменную кирку spirit-1", "кирку бочку"),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_MultipleWords_AllMustMatch проверь это утверждение.");
    }

    @Test
    void query_MissingWord_DoesNotMatch() {
        assertFalse(
                BoardSearch.matches("Сделать кирку", "бочку"),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене query_MissingWord_DoesNotMatch проверь это утверждение.");
    }
}
