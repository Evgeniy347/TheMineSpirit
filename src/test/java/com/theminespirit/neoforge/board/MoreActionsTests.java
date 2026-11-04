package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.More4Rules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MoreActionsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> MORE3 = List.of(
            "secure_danger",
            "recover_death_drop",
            "retune_work_cost",
            "clear_trash",
            "assign_chest_purpose",
            "spread_work",
            "use_idle_station",
            "home_night_work",
            "repair_world_decay",
            "raise_bottleneck_weight");
    private static final List<String> MORE4 = List.of(
            "more4_guard_settlement",
            "more4_watch_chests",
            "more4_bring_player_food",
            "more4_lend_player_tool",
            "more4_switch_tool",
            "more4_guard_portal",
            "more4_fence_portal",
            "more4_plan_expansion",
            "more4_find_space",
            "more4_guard_with_pet",
            "more4_leave_pet_alone",
            "more4_store_rare",
            "more4_spare_rare",
            "more4_sort_rare",
            "more4_feed_hungry",
            "more4_rest_injured");

    @Test
    void wayExecutors_LoadGraph_MatchImplementedFlag() {
        for (String way : MORE3) {
            assertEquals(GRAPH.implemented(way), More3Post.handles(way), "Исполнитель more3 для " + way);
        }
        for (String way : MORE4) {
            assertEquals(GRAPH.implemented(way), More4Post.handles(way), "Исполнитель more4 для " + way);
        }
    }

    @Test
    void patrolParams_WalkAroundPoint_WithoutBreakingWorld() {
        Map<String, Object> params = More4Scripts.patrolParams(new BlockCoord(10, 64, -5));

        @SuppressWarnings("unchecked")
        List<String> points = (List<String>) params.get("points");
        assertEquals(5, points.size(), "Патруль обходит центр и четыре стороны");
        assertEquals("loop", params.get("mode"), "Патруль ходит кругом");
        assertEquals(1, params.get("loops"), "Один круг за задание");
        assertTrue(points.contains("10,64,-5"), "Центр входит в маршрут");
        assertTrue(points.contains("14,64,-5"), "Соседняя точка маршрута на месте");
    }

    @Test
    void toolBlock_RequiredKind_PicksKnownBlock() {
        assertEquals("stone", More4Scripts.toolBlock("pickaxe"), "Кирка выбирается по камню");
        assertEquals("oak_log", More4Scripts.toolBlock("axe"), "Топор выбирается по бревну");
        assertEquals("grass_block", More4Scripts.toolBlock("hoe"), "Тяпка выбирается по траве");
        assertEquals("stone", More4Scripts.toolBlock("unknown"), "Неизвестный вид не оставляет духа без инструмента");
    }

    @Test
    void rareItem_FindsFirstRareWithNamespace_AndIgnoresOrdinary() {
        Set<String> rares = Set.of("diamond", "emerald");

        assertEquals(
                "minecraft:diamond",
                More4Scripts.rareItem(
                                Map.of("minecraft:dirt", 5, "minecraft:emerald", 2, "minecraft:diamond", 1), rares)
                        .orElseThrow(),
                "Редкий предмет находится в инвентаре детерминированно, а не по порядку карты");
        assertEquals(
                "diamond",
                More4Scripts.rareItem(Map.of("diamond", 1), rares).orElseThrow(),
                "Имя без пространства тоже считается редким");
        assertTrue(
                More4Scripts.rareItem(Map.of("minecraft:dirt", 5), rares).isEmpty(),
                "Обычный предмет не выдаётся за редкий");
        assertTrue(
                More4Scripts.rareItem(Map.of("minecraft:diamond", 0), rares).isEmpty(),
                "Нулевой остаток не считается находкой");
    }

    @Test
    void near_ChecksDangerRadius_WithEdges() {
        BlockCoord origin = new BlockCoord(0, 64, 0);
        assertTrue(More3Post.near(origin, new BlockCoord(8, 64, 0), 8), "Граница радиуса входит");
        assertFalse(More3Post.near(origin, new BlockCoord(9, 64, 0), 8), "За границей радиуса опасность не считается");
        assertFalse(More3Post.near(origin, new BlockCoord(0, 73, 0), 8), "Высота учитывается в расстоянии");
    }

    @Test
    void rareGroup_FragmentLoad_IsDeclared() {
        assertTrue(GRAPH.groups().containsKey(More4Rules.RARE), "Группа редких предметов объявлена");
    }
}
