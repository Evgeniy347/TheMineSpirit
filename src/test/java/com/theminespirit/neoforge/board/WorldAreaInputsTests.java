package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.observation.BlockCoord;
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

        List<GraphFacts.Stray> strays = WorldAreaInputs.strays(placed, at -> at.x() < 4, at -> at.x() == 2);

        assertEquals(2, strays.size(), "След вне участка и след в плане не считаются лишними");
        assertEquals("oak_planks", strays.get(0).type(), "Тип лишней постройки берётся из журнала");
        assertEquals("spirit-1", strays.get(0).builder(), "Кто ставил — переносится в факт");
        assertEquals(
                new GraphFacts.Position(1, 64, 1), strays.get(0).position(), "Координата следа переносится в факт");
        assertEquals("stone", strays.get(1).type(), "Второй лишний след сохраняется");
        assertEquals(
                Map.of("oak_planks", 1),
                strays.get(0).blocks(),
                "у лишней постройки есть состав блоков: по нему считается возврат материалов");
    }

    @Test
    void strays_SameCell_KeepsNewest() {
        List<WorldAreaInputs.Placed> placed = List.of(
                new WorldAreaInputs.Placed(new BlockCoord(1, 64, 1), "stone", "new"),
                new WorldAreaInputs.Placed(new BlockCoord(1, 64, 1), "dirt", "old"),
                new WorldAreaInputs.Placed(new BlockCoord(2, 64, 2), "dirt", null));

        List<GraphFacts.Stray> strays = WorldAreaInputs.strays(placed, at -> true, at -> false);

        assertEquals(2, strays.size(), "Один след на клетку: повтор из журнала не размножается");
        assertEquals("stone", first(strays, 1).type(), "Новая запись журнала главнее старой");
        assertEquals("new", first(strays, 1).builder(), "Строитель новой записи сохраняется");
        assertNull(first(strays, 2).builder(), "Неизвестный строитель остаётся null, а не выдумывается");
    }

    @Test
    void terrain_HolesAndExtras_CountDepthAndDrop() {
        Map<BlockCoord, Integer> depths = new LinkedHashMap<>();
        depths.put(new BlockCoord(1, 63, 1), 2);
        depths.put(new BlockCoord(2, 63, 2), 5);

        GraphFacts.Terrain terrain = WorldAreaInputs.terrain(
                List.of(new BlockCoord(1, 63, 1), new BlockCoord(2, 63, 2)),
                depths,
                List.of(new BlockCoord(3, 66, 3)),
                4,
                7);

        assertEquals(2, terrain.pits(), "Число ям — число клеток с глубиной");
        assertEquals(5, terrain.deepestPit(), "Самая глубокая яма задаёт глубину");
        assertEquals(3, terrain.heightDrop(), "Перепад — от нижней ямы до верхнего лишнего блока");
        assertEquals(4, terrain.hanging(), "Висящее переносится");
        assertEquals(7, terrain.traces(), "Следы вне плана переносятся");
        assertTrue(!terrain.flat(), "Неровный участок не считается ровным");
    }

    @Test
    void terrain_EmptyOrNegative_Handled() {
        GraphFacts.Terrain flat = WorldAreaInputs.terrain(List.of(), Map.of(), List.of(), 0, 0);

        assertEquals(GraphFacts.Terrain.empty(), flat, "Пустой вход даёт ровный участок");
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldAreaInputs.terrain(List.of(), Map.of(), List.of(), -1, 0),
                "Отрицательное висящее не имеет смысла");
    }

    @Test
    void buildings_HouseAudit_GivesPercentAndDamage() {
        Map<String, GraphFacts.Building> base = new LinkedHashMap<>();
        base.put(
                "hut",
                new GraphFacts.Building(
                        "hut", 1, 100, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(0, 64, 0), false));
        base.put(
                "mine",
                new GraphFacts.Building(
                        "mine", 1, 100, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(8, 64, 0), false));
        base.put("fence", new GraphFacts.Building("fence", 0, 0, 0, Map.of(), Map.of(), 0, null, false));

        Map<String, GraphFacts.Building> facts =
                WorldAreaInputs.buildings(base, new RepairAudit.Report(20, 5, false, List.of(), 0));

        assertEquals(75, facts.get("hut").auditPercent(), "Процент — сколько целых клеток схемы");
        assertEquals(5, facts.get("hut").damage(), "Повреждения — недостача аудита");
        assertTrue(!facts.get("hut").unknown(), "Аудит без незагруженных чанков считается известным");
        assertTrue(facts.get("mine").unknown(), "Постройка без аудита остаётся неизвестной");
        assertEquals(0, facts.get("fence").auditPercent(), "Непостроенная постройка — ноль процентов");
        assertTrue(!facts.get("fence").unknown(), "Отсутствие известно: это не unknown");
    }

    @Test
    void buildings_UnknownAudit_IsNotReady() {
        Map<String, GraphFacts.Building> base = new LinkedHashMap<>();
        base.put(
                "hut",
                new GraphFacts.Building(
                        "hut", 1, 100, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(0, 64, 0), false));

        Map<String, GraphFacts.Building> facts =
                WorldAreaInputs.buildings(base, new RepairAudit.Report(20, 5, false, List.of(), 2));

        assertTrue(facts.get("hut").unknown(), "Незагруженные чанки делают аудит неизвестным");
        assertEquals(0, facts.get("hut").damage(), "Пока аудит не готов, повреждений не выдумываем");
        assertEquals(0, facts.get("hut").auditPercent(), "Пока аудит не готов, процент не выдумываем");
    }

    private static GraphFacts.Stray first(List<GraphFacts.Stray> strays, int x) {
        return strays.stream()
                .filter(stray -> stray.position().x() == x)
                .findFirst()
                .orElseThrow();
    }
}
