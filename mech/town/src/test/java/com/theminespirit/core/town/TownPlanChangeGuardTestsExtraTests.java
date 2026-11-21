package com.theminespirit.core.town;

import static com.theminespirit.core.town.TownPlanChangeGuardTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TownPlanChangeGuardTestsExtraTests {

    @Test
    void savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot() {
        TownPlan base = plan();
        TownPlan old = new TownPlan(
                base.anchor(),
                base.region(),
                base.streets(),
                base.placements().stream()
                        .filter(place -> !"wheat_field".equals(place.id()))
                        .toList(),
                base.fence(),
                base.unplaced());
        TownPlan decoded = TownPlanCodec.decode(TownPlanCodec.encode(old));

        TownPlan grown = TownPlanner.plant(grid(), decoded, RULES, STAGES, List.of(), 1);

        assertTrue(
                grown.placement("wheat_field").isPresent(),
                "Старый план обязан получить участок грядки"
                        + " Иначе тест savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot перестанет ловить эту поломку. При смене savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot проверь это утверждение.");
        for (Placement before : decoded.placements()) {
            assertEquals(
                    before,
                    grown.placement(before.id()).orElseThrow(),
                    "Переезд грядки не имеет права двигать чужой участок '" + before.id() + "'"
                            + " Иначе тест savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot перестанет ловить эту поломку. При смене savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot проверь это утверждение.");
        }
        assertEquals(
                decoded.anchor(),
                grown.anchor(),
                "Origin поселения при миграции не меняется"
                        + " Иначе тест savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot перестанет ловить эту поломку. При смене savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot проверь это утверждение.");
        assertPlacementsApart(grown);
    }

    @Test
    void change_AnyOneScheme_KeepsOtherSchemeAudit() {
        Schematic first = scheme("wheat_field_1");
        Schematic second = scheme("field_1");
        Map<BlockCoord, String> world = new HashMap<>();
        for (Schematic.PlacedEntry entry : second.entries(ANCHOR)) {
            world.put(entry.pos(), entry.block());
        }
        int before = audit(second, world).total();

        assertTrue(
                first.sizeX() != second.sizeX() || first.sizeZ() != second.sizeZ(),
                "Схемы обязаны отличаться габаритом: иначе тест не меняет чужую схему"
                        + " При смене change_AnyOneScheme_KeepsOtherSchemeAudit проверь это утверждение.");
        assertEquals(
                before,
                audit(second, world).total(),
                "Чужая схема не меняет аудит этой постройки"
                        + " Иначе тест change_AnyOneScheme_KeepsOtherSchemeAudit перестанет ловить эту поломку. При смене change_AnyOneScheme_KeepsOtherSchemeAudit проверь это утверждение.");
        assertEquals(
                before,
                audit(second, world).total(),
                "Повторный аудит после смены другой схемы обязан дать тот же результат"
                        + " Иначе тест change_AnyOneScheme_KeepsOtherSchemeAudit перестанет ловить эту поломку. При смене change_AnyOneScheme_KeepsOtherSchemeAudit проверь это утверждение.");
    }
}
