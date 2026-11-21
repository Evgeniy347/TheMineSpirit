package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StrayGroups;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldAreaInputsTests {
    @Test
    void strays_UnplannedInside_KeptWithBuilder() {
        List<WorldAreaInputs.Placed> placed = List.of(
                new WorldAreaInputs.Placed(new BlockCoord(1, 64, 1), "oak_planks", "spirit-1"),
                new WorldAreaInputs.Placed(new BlockCoord(2, 64, 2), "dirt", "spirit-2"),
                new WorldAreaInputs.Placed(new BlockCoord(3, 64, 3), "stone", "spirit-3"),
                new WorldAreaInputs.Placed(new BlockCoord(4, 64, 4), "sand", "spirit-4"));

        List<GraphSiteFacts.Stray> strays = WorldAreaInputs.strays(placed, at -> at.x() < 4, at -> at.x() == 2);

        assertEquals(
                2,
                strays.size(),
                "След вне участка и след в плане не считаются лишними"
                        + " Иначе тест strays_UnplannedInside_KeptWithBuilder перестанет ловить эту поломку. При смене strays_UnplannedInside_KeptWithBuilder проверь это утверждение.");
        assertEquals(
                "oak_planks",
                strays.get(0).type(),
                "Тип лишней постройки берётся из журнала"
                        + " Иначе тест strays_UnplannedInside_KeptWithBuilder перестанет ловить эту поломку. При смене strays_UnplannedInside_KeptWithBuilder проверь это утверждение.");
        assertEquals(
                "spirit-1",
                strays.get(0).builder(),
                "Кто ставил — переносится в факт"
                        + " Иначе тест strays_UnplannedInside_KeptWithBuilder перестанет ловить эту поломку. При смене strays_UnplannedInside_KeptWithBuilder проверь это утверждение.");
        assertEquals(
                new GraphFacts.Position(1, 64, 1),
                strays.get(0).position(),
                "Координата следа переносится в факт"
                        + " Иначе тест strays_UnplannedInside_KeptWithBuilder перестанет ловить эту поломку. При смене strays_UnplannedInside_KeptWithBuilder проверь это утверждение.");
        assertEquals(
                "stone",
                strays.get(1).type(),
                "Второй лишний след сохраняется"
                        + " Иначе тест strays_UnplannedInside_KeptWithBuilder перестанет ловить эту поломку. При смене strays_UnplannedInside_KeptWithBuilder проверь это утверждение.");
        assertEquals(
                Map.of("oak_planks", 1),
                strays.get(0).blocks(),
                "у лишней постройки есть состав блоков: по нему считается возврат материалов"
                        + " Иначе тест strays_UnplannedInside_KeptWithBuilder перестанет ловить эту поломку. При смене strays_UnplannedInside_KeptWithBuilder проверь это утверждение.");
    }

    @Test
    void strays_SameCell_KeepsNewest() {
        List<WorldAreaInputs.Placed> placed = List.of(
                new WorldAreaInputs.Placed(new BlockCoord(1, 64, 1), "stone", "new"),
                new WorldAreaInputs.Placed(new BlockCoord(1, 64, 1), "dirt", "old"),
                new WorldAreaInputs.Placed(new BlockCoord(2, 64, 2), "dirt", null));

        List<GraphSiteFacts.Stray> strays = WorldAreaInputs.strays(placed, at -> true, at -> false);

        assertEquals(
                2,
                strays.size(),
                "Один след на клетку: повтор из журнала не размножается"
                        + " Иначе тест strays_SameCell_KeepsNewest перестанет ловить эту поломку. При смене strays_SameCell_KeepsNewest проверь это утверждение.");
        assertEquals(
                "stone",
                first(strays, 1).type(),
                "Новая запись журнала главнее старой"
                        + " Иначе тест strays_SameCell_KeepsNewest перестанет ловить эту поломку. При смене strays_SameCell_KeepsNewest проверь это утверждение.");
        assertEquals(
                "new",
                first(strays, 1).builder(),
                "Строитель новой записи сохраняется"
                        + " Иначе тест strays_SameCell_KeepsNewest перестанет ловить эту поломку. При смене strays_SameCell_KeepsNewest проверь это утверждение.");
        assertNull(
                first(strays, 2).builder(),
                "Неизвестный строитель остаётся null, а не выдумывается"
                        + " Иначе тест strays_SameCell_KeepsNewest перестанет ловить эту поломку. При смене strays_SameCell_KeepsNewest проверь это утверждение.");
    }

    @Test
    void strays_WorldScan_GroupsConnectedBlocksAndKeepsJournalBuilder() {
        SettlementScan.Strays found = new SettlementScan.Strays(
                Map.of(),
                List.of(),
                List.of(
                        new StrayGroups.Cell(new BlockCoord(1, 64, 1), "minecraft:oak_fence"),
                        new StrayGroups.Cell(new BlockCoord(2, 64, 1), "minecraft:oak_fence")),
                0);
        List<WorldAreaInputs.Placed> placed =
                List.of(new WorldAreaInputs.Placed(new BlockCoord(2, 64, 1), "oak_fence", "spirit-1"));

        List<GraphSiteFacts.Stray> strays = WorldAreaInputs.strays(found, placed);

        assertEquals(
                1,
                strays.size(),
                "связные клетки сканера — один кусок лишнего"
                        + " Иначе тест strays_WorldScan_GroupsConnectedBlocksAndKeepsJournalBuilder перестанет ловить эту поломку. При смене strays_WorldScan_GroupsConnectedBlocksAndKeepsJournalBuilder проверь это утверждение.");
        assertEquals(
                Map.of("oak_fence", 2),
                strays.get(0).blocks(),
                "у куска есть тип и количество"
                        + " Иначе тест strays_WorldScan_GroupsConnectedBlocksAndKeepsJournalBuilder перестанет ловить эту поломку. При смене strays_WorldScan_GroupsConnectedBlocksAndKeepsJournalBuilder проверь это утверждение.");
        assertEquals(
                "spirit-1",
                strays.get(0).builder(),
                "строитель куска берётся из журнала, если запись есть"
                        + " Иначе тест strays_WorldScan_GroupsConnectedBlocksAndKeepsJournalBuilder перестанет ловить эту поломку. При смене strays_WorldScan_GroupsConnectedBlocksAndKeepsJournalBuilder проверь это утверждение.");
    }

    @Test
    void terrain_UnknownChunk_IsNotReportedFlat() {
        GraphSiteFacts.Terrain terrain = WorldAreaInputs.terrain(
                List.of(), Map.of(), List.of(), 0, new WorldAreaInputs.Marks(Map.of(), Map.of(), Map.of()), true);

        assertTrue(
                terrain.unknown(),
                "незагруженный чанк делает рельеф неизвестным"
                        + " Иначе тест terrain_UnknownChunk_IsNotReportedFlat перестанет ловить эту поломку. При смене terrain_UnknownChunk_IsNotReportedFlat проверь это утверждение.");
        assertEquals(
                0,
                terrain.pits(),
                "по неизвестному рельефу ям не выдумываем"
                        + " Иначе тест terrain_UnknownChunk_IsNotReportedFlat перестанет ловить эту поломку. При смене terrain_UnknownChunk_IsNotReportedFlat проверь это утверждение.");
    }

    @Test
    void terrain_HolesAndExtras_CountDepthAndDrop() {
        Map<BlockCoord, Integer> depths = new LinkedHashMap<>();
        depths.put(new BlockCoord(1, 63, 1), 2);
        depths.put(new BlockCoord(2, 63, 2), 5);

        GraphSiteFacts.Terrain terrain = WorldAreaInputs.terrain(
                List.of(new BlockCoord(1, 63, 1), new BlockCoord(2, 63, 2)),
                depths,
                List.of(new BlockCoord(3, 66, 3)),
                4);

        assertEquals(
                2,
                terrain.pits(),
                "Число ям — число клеток с глубиной"
                        + " Иначе тест terrain_HolesAndExtras_CountDepthAndDrop перестанет ловить эту поломку. При смене terrain_HolesAndExtras_CountDepthAndDrop проверь это утверждение.");
        assertEquals(
                5,
                terrain.deepestPit(),
                "Самая глубокая яма задаёт глубину"
                        + " Иначе тест terrain_HolesAndExtras_CountDepthAndDrop перестанет ловить эту поломку. При смене terrain_HolesAndExtras_CountDepthAndDrop проверь это утверждение.");
        assertEquals(
                3,
                terrain.heightDrop(),
                "Перепад — от нижней ямы до верхнего лишнего блока"
                        + " Иначе тест terrain_HolesAndExtras_CountDepthAndDrop перестанет ловить эту поломку. При смене terrain_HolesAndExtras_CountDepthAndDrop проверь это утверждение.");
        assertEquals(
                4,
                terrain.hanging(),
                "Висящее переносится"
                        + " Иначе тест terrain_HolesAndExtras_CountDepthAndDrop перестанет ловить эту поломку. При смене terrain_HolesAndExtras_CountDepthAndDrop проверь это утверждение.");
        assertTrue(
                terrain.pits() > 0,
                "Неровный участок виден по ямам, а не по выдуманному флагу"
                        + " Иначе тест terrain_HolesAndExtras_CountDepthAndDrop перестанет ловить эту поломку. При смене terrain_HolesAndExtras_CountDepthAndDrop проверь это утверждение.");
    }

    @Test
    void terrain_NaturalSlope_IsNotWork() {
        GraphSiteFacts.Terrain terrain = WorldAreaInputs.terrain(
                List.of(), Map.of(), List.of(), 0, new WorldAreaInputs.Marks(Map.of(), Map.of(), Map.of(), 17), false);

        assertEquals(
                17,
                terrain.natural(),
                "природный склон учитывается отдельно от насыпей лота"
                        + " Иначе тест terrain_NaturalSlope_IsNotWork перестанет ловить эту поломку. При смене terrain_NaturalSlope_IsNotWork проверь это утверждение.");
        assertTrue(
                terrain.cut().isEmpty(),
                "природный склон не подаётся в cut как работа"
                        + " Иначе тест terrain_NaturalSlope_IsNotWork перестанет ловить эту поломку. При смене terrain_NaturalSlope_IsNotWork проверь это утверждение.");
    }

    @Test
    void terrain_EmptyOrNegative_Handled() {
        GraphSiteFacts.Terrain flat = WorldAreaInputs.terrain(List.of(), Map.of(), List.of(), 0);

        assertEquals(
                GraphSiteFacts.Terrain.empty(),
                flat,
                "Пустой вход даёт ровный участок"
                        + " Иначе тест terrain_EmptyOrNegative_Handled перестанет ловить эту поломку. При смене terrain_EmptyOrNegative_Handled проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldAreaInputs.terrain(List.of(), Map.of(), List.of(), -1),
                "Отрицательное висящее не имеет смысла"
                        + " Иначе тест terrain_EmptyOrNegative_Handled перестанет ловить эту поломку. При смене terrain_EmptyOrNegative_Handled проверь это утверждение.");
    }

    @Test
    void buildings_HouseAudit_GivesPercentAndDamage() {
        Map<String, GraphFacts.Building> base = new LinkedHashMap<>();
        base.put(
                "hut",
                new GraphFacts.Building(
                        "hut", 1, 100, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(0, 64, 0), false, true));
        base.put(
                "mine",
                new GraphFacts.Building(
                        "mine", 1, 0, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(8, 64, 0), true, true));
        base.put("fence", new GraphFacts.Building("fence", 0, 0, 0, Map.of(), Map.of(), 0, null, false, true));

        Map<String, GraphFacts.Building> facts =
                WorldAreaInputs.buildings(base, new RepairAudit.Report(20, 5, false, List.of(), 0));

        assertEquals(
                75,
                facts.get("hut").auditPercent(),
                "Процент — сколько целых клеток схемы"
                        + " Иначе тест buildings_HouseAudit_GivesPercentAndDamage перестанет ловить эту поломку. При смене buildings_HouseAudit_GivesPercentAndDamage проверь это утверждение.");
        assertEquals(
                5,
                facts.get("hut").damage(),
                "Повреждения — недостача аудита"
                        + " Иначе тест buildings_HouseAudit_GivesPercentAndDamage перестанет ловить эту поломку. При смене buildings_HouseAudit_GivesPercentAndDamage проверь это утверждение.");
        assertTrue(
                !facts.get("hut").unknown(),
                "Аудит без незагруженных чанков считается известным"
                        + " Иначе тест buildings_HouseAudit_GivesPercentAndDamage перестанет ловить эту поломку. При смене buildings_HouseAudit_GivesPercentAndDamage проверь это утверждение.");
        assertTrue(
                facts.get("mine").unknown(),
                "Постройка без аудита остаётся неизвестной"
                        + " Иначе тест buildings_HouseAudit_GivesPercentAndDamage перестанет ловить эту поломку. При смене buildings_HouseAudit_GivesPercentAndDamage проверь это утверждение.");
        assertEquals(
                0,
                facts.get("fence").auditPercent(),
                "Непостроенная постройка — ноль процентов"
                        + " Иначе тест buildings_HouseAudit_GivesPercentAndDamage перестанет ловить эту поломку. При смене buildings_HouseAudit_GivesPercentAndDamage проверь это утверждение.");
        assertTrue(
                !facts.get("fence").unknown(),
                "Отсутствие известно: это не unknown"
                        + " Иначе тест buildings_HouseAudit_GivesPercentAndDamage перестанет ловить эту поломку. При смене buildings_HouseAudit_GivesPercentAndDamage проверь это утверждение.");
    }

    @Test
    void buildings_CompleteAudited_IsKnownNotUnknown() {
        Map<String, GraphFacts.Building> base = new LinkedHashMap<>();
        base.put(
                "mine",
                new GraphFacts.Building(
                        "mine", 1, 100, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(8, 64, 0), false, true));

        Map<String, GraphFacts.Building> facts = WorldAreaInputs.buildings(base, null);

        assertTrue(
                !facts.get("mine").unknown(),
                "Аудит 100% без незагруженных чанков — известная готовая постройка: иначе этап не закрывается"
                        + " При смене buildings_CompleteAudited_IsKnownNotUnknown проверь это утверждение.");
        assertEquals(
                100,
                facts.get("mine").auditPercent(),
                "Процент готовой постройки остаётся 100"
                        + " Иначе тест buildings_CompleteAudited_IsKnownNotUnknown перестанет ловить эту поломку. При смене buildings_CompleteAudited_IsKnownNotUnknown проверь это утверждение.");
    }

    private static GraphSiteFacts.Stray first(List<GraphSiteFacts.Stray> strays, int x) {
        return strays.stream()
                .filter(stray -> stray.position().x() == x)
                .findFirst()
                .orElseThrow();
    }
}
