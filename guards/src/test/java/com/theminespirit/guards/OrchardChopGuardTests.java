package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OrchardChopGuardTests {
    private static final Path NEOFORGE = SourceFiles.path("neoforge");

    @Test
    void chopSearch_OrchardTerritoryPolicy_IsWired() {
        String source = SourceFiles.javaUnder(NEOFORGE).get(NEOFORGE.resolve("ChopSearch.java"));

        assertTrue(
                source != null && source.contains("OrchardChop.allowed("),
                "ChopSearch снова выбирает деревья без проверки территории сада: духи уйдут рубить дикий лес "
                        + "за озером, утонут и не вернутся; фоновая рубка обязана спрашивать OrchardChop.allowed "
                        + "и брать только клетки сада"
                        + " Иначе тест chopSearch_OrchardTerritoryPolicy_IsWired перестанет ловить эту поломку. При смене ChopSearch проверь живую рубку: деревья вне сада не выбираются.");
    }
}
