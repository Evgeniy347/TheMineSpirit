package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrchardSitesTests {
    @Test
    void isExtra_OakBuilding_True() {
        assertTrue(
                OrchardSites.isExtra("orchard_oak"),
                "Дуб больше не особая порода: сад дуба может стоять отдельным участком, решает спрос"
                        + " Иначе тест isExtra_OakBuilding_True перестанет ловить эту поломку. При смене isExtra_OakBuilding_True проверь это утверждение.");
    }

    @Test
    void speciesOfBuilding_NumberedOrchards_BaseSpecies() {
        assertEquals(
                Optional.of("jungle"),
                OrchardSites.speciesOfBuilding("orchard_jungle_2"),
                "Номер участка не должен менять породу: orchard_jungle_2 — сад джунглей"
                        + " Иначе тест speciesOfBuilding_NumberedOrchards_BaseSpecies перестанет ловить эту поломку. При смене speciesOfBuilding_NumberedOrchards_BaseSpecies проверь это утверждение.");
        assertEquals(
                Optional.of("dark_oak"),
                OrchardSites.speciesOfBuilding("orchard_dark_oak_3"),
                "Порода с подчёркиванием (dark_oak) разбирается вместе с номером участка"
                        + " Иначе тест speciesOfBuilding_NumberedOrchards_BaseSpecies перестанет ловить эту поломку. При смене speciesOfBuilding_NumberedOrchards_BaseSpecies проверь это утверждение.");
    }

    @Test
    void baseBuilding_Numbered_StripsNumber() {
        assertEquals(
                "orchard_jungle",
                OrchardSites.baseBuilding("orchard_jungle_2"),
                "Для заданий и аудита все участки породы сводятся к её базовой постройке"
                        + " Иначе тест baseBuilding_Numbered_StripsNumber перестанет ловить эту поломку. При смене baseBuilding_Numbered_StripsNumber проверь это утверждение.");
    }

    @Test
    void numbered_Second_AddsSuffix() {
        assertEquals(
                "orchard_jungle_2",
                OrchardSites.numbered("orchard_jungle", 2),
                "Второй сад породы получает номер, чтобы место и задание были уникальны"
                        + " Иначе тест numbered_Second_AddsSuffix перестанет ловить эту поломку. При смене numbered_Second_AddsSuffix проверь это утверждение.");
        assertEquals(
                "orchard_jungle",
                OrchardSites.numbered("orchard_jungle", 1),
                "Первый сад породы остаётся без номера: совместимость с живым планом"
                        + " Иначе тест numbered_Second_AddsSuffix перестанет ловить эту поломку. При смене numbered_Second_AddsSuffix проверь это утверждение.");
    }

    @Test
    void wantAnother_PreviousNotBuilt_False() {
        assertFalse(
                OrchardSites.wantAnother(false, 64, 4),
                "Пока прошлый сад породы не построен, новый не просят: иначе план плодит пустые участки"
                        + " При смене wantAnother_PreviousNotBuilt_False проверь это утверждение.");
    }

    @Test
    void wantAnother_BuiltAndStock_True() {
        assertTrue(
                OrchardSites.wantAnother(true, 4, 4),
                "Построен прошлый сад и хватает саженцев на новый — спрос законный"
                        + " Иначе тест wantAnother_BuiltAndStock_True перестанет ловить эту поломку. При смене wantAnother_BuiltAndStock_True проверь это утверждение.");
    }

    @Test
    void wantAnother_BuiltNoStock_False() {
        assertFalse(
                OrchardSites.wantAnother(true, 3, 4),
                "Без саженцев на постройку новый сад не просят: стройка встала бы без материалов"
                        + " Иначе тест wantAnother_BuiltNoStock_False перестанет ловить эту поломку. При смене wantAnother_BuiltNoStock_False проверь это утверждение.");
    }

    @Test
    void plotIds_SpeciesPlots_EachPlotAlone() {
        List<OrchardPlot> plots = List.of(plot("orchard_jungle"), plot("orchard_jungle_2"));
        assertEquals(
                List.of("orchard_jungle", "orchard_jungle_2"),
                OrchardSites.plotIds("orchard_jungle", true, plots),
                "У каждого участка породы своё задание: ремонт одного не чинит другой"
                        + " Иначе тест plotIds_SpeciesPlots_EachPlotAlone перестанет ловить эту поломку. При смене plotIds_SpeciesPlots_EachPlotAlone проверь это утверждение.");
        assertEquals(
                List.of(),
                OrchardSites.plotIds("orchard_dark_oak", true, plots),
                "Чужие сады в ремонт породы не попадают: участок отвечает только за свою породу"
                        + " Иначе тест plotIds_SpeciesPlots_EachPlotAlone перестанет ловить эту поломку. При смене plotIds_SpeciesPlots_EachPlotAlone проверь это утверждение.");
    }

    @Test
    void plotIds_MainOak_IncludesMainAndNumbered() {
        List<OrchardPlot> plots = List.of(plot("orchard_oak_2"));
        assertEquals(
                List.of("orchard", "orchard_oak_2"),
                OrchardSites.plotIds("orchard_oak", true, plots),
                "Главный сад — отдельный участок дуба, номерной сад дуба — второй"
                        + " Иначе тест plotIds_MainOak_IncludesMainAndNumbered перестанет ловить эту поломку. При смене plotIds_MainOak_IncludesMainAndNumbered проверь это утверждение.");
        assertEquals(
                List.of("orchard_oak_2"),
                OrchardSites.plotIds("orchard_oak", false, plots),
                "Без главного сада на плане ремонт берёт только номерные участки"
                        + " Иначе тест plotIds_MainOak_IncludesMainAndNumbered перестанет ловить эту поломку. При смене plotIds_MainOak_IncludesMainAndNumbered проверь это утверждение.");
    }

    private static OrchardPlot plot(String building) {
        Placement place = new Placement(building, building, 1, 0, 0, 13, 13, Placement.Facing.NORTH);
        return new OrchardPlot(
                place, building, OrchardSites.speciesOfBuilding(building).orElseThrow(), List.of());
    }
}
