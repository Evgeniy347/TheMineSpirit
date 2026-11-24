package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlanNamesTests {
    @Test
    void title_TranslatedName_Wins() {
        assertEquals(
                "Хижина",
                PlanNames.title("family_house_a", id -> "Хижина"),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене title_TranslatedName_Wins проверь это утверждение.");
    }

    @Test
    void title_MissingTranslation_FallsBackToReadableText() {
        assertEquals(
                "Family house a",
                PlanNames.title("family_house_a", id -> null),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене title_MissingTranslation_FallsBackToReadableText проверь это утверждение.");
        assertEquals(
                "Family house a",
                PlanNames.title("family_house_a", null),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене title_MissingTranslation_FallsBackToReadableText проверь это утверждение.");
    }

    @Test
    void title_BlankTranslation_IsIgnored() {
        assertEquals(
                "Sklad 2",
                PlanNames.title("sklad_2", id -> "  "),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене title_BlankTranslation_IsIgnored проверь это утверждение.");
    }
}
