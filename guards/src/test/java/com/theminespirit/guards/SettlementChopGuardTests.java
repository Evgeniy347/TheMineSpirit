package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettlementChopGuardTests {
    private static final Path NEOFORGE = SourceFiles.path("neoforge");

    @Test
    void chopSearch_SettlementTerritoryPolicy_IsWired() {
        String source = SourceFiles.javaUnder(NEOFORGE).get(NEOFORGE.resolve("ChopSearch.java"));

        assertTrue(
                source != null && source.contains("TreeClearance.allowed("),
                "ChopSearch снова выбирает деревья без проверки территории поселения: духи уйдут рубить дикий лес "
                        + "за озером, утонут и не вернутся; фоновая рубка обязана спрашивать TreeClearance.allowed "
                        + "и брать только свободные клетки зоны"
                        + " Иначе тест chopSearch_SettlementTerritoryPolicy_IsWired перестанет ловить эту поломку. При смене ChopSearch проверь живую рубку: деревья вне зоны и план не выбираются.");
        assertTrue(
                source == null || !source.contains("OrchardChop.allowed("),
                "ChopSearch снова ограничен только садом: рубка по всей зоне поселения должна идти через "
                        + "TreeClearance, а OrchardChop остаётся для садового ухода, иначе свободные деревья зоны "
                        + "остаются стоять и заготовка встаёт без материала"
                        + " Иначе тест chopSearch_SettlementTerritoryPolicy_IsWired перестанет ловить эту поломку. При смене ChopSearch проверь правила очистки зоны.");
    }
}
